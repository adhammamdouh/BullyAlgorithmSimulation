import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Process {
    protected static final int PORT_BASE = 5000;
    protected static final int COORDINATOR_TIMEOUT_IN_MS = 3000;
    protected static final int ALIVE_MESSAGE_INTERVAL_IN_MS = COORDINATOR_TIMEOUT_IN_MS / 2;
    protected static final int ELECTION_TIMEOUT_IN_MS = 2000;

    protected int id;
    protected int port;
    protected boolean isCoordinator;
    protected int coordinatorId;
    protected AtomicBoolean isElectionInProgress;
    protected long electionStartTime;
    protected long lastAliveMessageTime;
    protected long latestCoordinatorTimestamp;

    protected List<ProcessInfo> otherProcesses;
    protected ServerSocket serverSocket;

    protected Thread coordinatorHeartbeatThread;
    protected Thread electionTimeoutThread;

    private JTextArea logArea;

    public Process(int id, JTextArea logArea) {
        this.id = id;
        this.port = PORT_BASE + id;
        this.isCoordinator = false;
        this.coordinatorId = -1; // Initially, no coordinator
        this.otherProcesses = new ArrayList<>();

        this.isElectionInProgress = new AtomicBoolean(false);
        this.logArea = logArea;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(COORDINATOR_TIMEOUT_IN_MS);

            if (!isCoordinator) {
                announceNewProcess();
                findCoordinator();
            }

            while (true) {
                try {
                    handleIncomingMessages();
                } catch (SocketException e) {
                    logArea.append("Process " + id + " stopped.\n");
                    break;
                }
            }

        } catch (IOException e) {
            // If the port is already in use, try the next one
        }
    }

    public void stop() {
        try {
            for (ProcessInfo processInfo : otherProcesses) {
                if (isCoordinator) {
                    sendMessage(processInfo, new Message(id, MessageType.COORDINATOR_STOP, processInfo.getId()));
                } else {
                    sendMessage(processInfo, new Message(id, MessageType.STOP, processInfo.getId()));
                }
            }

            isCoordinator = false;
            coordinatorId = -1;

            serverSocket.close();
            stopHeartbeatThread();

            if (electionTimeoutThread != null) {
                electionTimeoutThread.interrupt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void announceNewProcess() {
        for (ProcessInfo processInfo : otherProcesses) {
            sendMessage(processInfo, new Message(id, MessageType.NEW_PROCESS, processInfo.getId()));
        }
    }

    private void findCoordinator() {
        initiateElection();
    }

    private void initiateElection() {
        isElectionInProgress.set(true);
        electionStartTime = System.currentTimeMillis();

        for (ProcessInfo processInfo : otherProcesses) {
            if (processInfo.getId() > id) {
                sendMessage(processInfo, new Message(id, MessageType.ELECTION, processInfo.getId()));
            }
        }

        startElectionTimeoutThread();
    }

    private void handleIncomingMessages() throws SocketException {
        try (
            Socket socket = serverSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String messageString = in.readLine();
            Message message = Message.fromString(messageString);

            logArea.append("Process " + id + " received: " + message.toLogString() + "\n");
            switch (message.getType()) {
                case NEW_PROCESS:
                    handleNewProcessMessage(message);
                    break;
                case ELECTION:
                    handleElectionMessage(message);
                    break;
                case COORDINATOR:
                    handleCoordinatorMessage(message);
                    break;
                case OK:
                    handleOkMessage(message);
                    break;
                case COORDINATOR_ALIVE:
                    handleCoordinatorAliveMessage(message);
                    break;
                case STOP:
                    handleStopMessage(message);
                    break;
                case COORDINATOR_STOP:
                    handleCoordinatorStopMessage(message);
                    break;
            }
        } catch (SocketTimeoutException e) {
            if (!isCoordinator) {
                checkCoordinatorAlive();
            }
        } catch (SocketException e) {
            throw e;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleNewProcessMessage(Message message) {
        if (!processExists(message.getSenderId())) {
            otherProcesses.add(new ProcessInfo(message.getSenderId(), PORT_BASE + message.getSenderId()));
        }
    }

    private void handleElectionMessage(Message message) {
        if (!isElectionInProgress.get()) {
            initiateElection();
        }

        sendMessage(new ProcessInfo(message.getSenderId(), PORT_BASE + message.getSenderId()),
                new Message(id, MessageType.OK, message.getSenderId()));
    }

    private void handleCoordinatorMessage(Message message) {
        if (isElectionInProgress.get()) {
            return;
        }
        isElectionInProgress.set(false);
        if (message.getTimestamp() >= latestCoordinatorTimestamp  && message.getSenderId() > id) {
            latestCoordinatorTimestamp = message.getTimestamp();
            coordinatorId = message.getSenderId();
        }
        stopHeartbeatThread();
        isCoordinator = false;
    }

    private void handleOkMessage(Message message) {
        isElectionInProgress.set(false);
    }

    private void handleCoordinatorAliveMessage(Message message) {
        lastAliveMessageTime = System.currentTimeMillis();
        if (message.getSenderId() > id) {
            stopHeartbeatThread();
            isCoordinator = false;
        }
    }

    private void handleStopMessage(Message message) {
        removeProcess(message.getSenderId());

        if (isElectionInProgress.get() && message.getSenderId() > id) {
            initiateElection();
        }
    }

    private void handleCoordinatorStopMessage(Message message) {
        isCoordinator = false;
        coordinatorId = -1;

        removeProcess(message.getSenderId());

        if (isElectionInProgress.get()) {
            initiateElection();
        }
    }

    private void removeProcess(int processId) {
        for (ProcessInfo processInfo : otherProcesses) {
            if (processInfo.getId() == processId) {
                otherProcesses.remove(processInfo);
                break;
            }
        }
    }

    private void checkCoordinatorAlive() {
        if (System.currentTimeMillis() - lastAliveMessageTime > COORDINATOR_TIMEOUT_IN_MS) {
            initiateElection();
        }
    }

    private void startElectionTimeoutThread() {
        electionTimeoutThread = new Thread(() -> {
            while (!electionTimeoutThread.isInterrupted()) {
                try {
                    Thread.sleep(ELECTION_TIMEOUT_IN_MS);
                    if (CheckElectionTimeout() && !isCoordinator) {
                        declareAsCoordinator();
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        electionTimeoutThread.start();
    }

    private boolean CheckElectionTimeout() {
        return isElectionInProgress.get() && System.currentTimeMillis() - electionStartTime > ELECTION_TIMEOUT_IN_MS;
    }

    private void declareAsCoordinator() {
        for (ProcessInfo processInfo : otherProcesses) {
            sendMessage(processInfo, new Message(id, MessageType.COORDINATOR, processInfo.getId()));
        }

        isCoordinator = true;
        coordinatorId = id;
        isElectionInProgress.set(false);
        startCoordinatorHeartbeatThread();
    }

    private void startCoordinatorHeartbeatThread() {
        coordinatorHeartbeatThread = new Thread(() -> {
            while (!coordinatorHeartbeatThread.isInterrupted()) {
                try {
                    Thread.sleep(ALIVE_MESSAGE_INTERVAL_IN_MS);
                    logArea.append("Process " + id + " sending alive message to other processes.\n");
                    sendCoordinatorAliveMessage();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        coordinatorHeartbeatThread.start();
    }

    private void sendCoordinatorAliveMessage() throws InterruptedException {
        for (ProcessInfo processInfo : otherProcesses) {
            sendMessage(processInfo, new Message(id, MessageType.COORDINATOR_ALIVE, processInfo.getId()));
        }

    }

    private void sendMessage(ProcessInfo receiver, Message message) {
        try (Socket socket = new Socket("localhost", receiver.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(message.toString());
        } catch (IOException e) {
        }
    }

    private boolean processExists(int processId) {
        return otherProcesses.stream().anyMatch(processInfo -> processInfo.getId() == processId);
    }

    private void stopHeartbeatThread() {
        if (coordinatorHeartbeatThread != null) {
            coordinatorHeartbeatThread.interrupt();
            try {
                coordinatorHeartbeatThread.join();
            } catch (InterruptedException e) {

            }
        }
    }


}
