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
                broadcastNewProcess();
                requestCoordinatorElection();
            }

            while (true) {
                try {
                    processIncomingMessages();
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
                    sendMessageToProcess(processInfo, new Message(id, MessageType.COORDINATOR_STOP, processInfo.getId()));
                } else {
                    sendMessageToProcess(processInfo, new Message(id, MessageType.STOP, processInfo.getId()));
                }
            }

            isCoordinator = false;
            coordinatorId = -1;

            serverSocket.close();
            terminateHeartbeatThread ();

            if (electionTimeoutThread != null) {
                electionTimeoutThread.interrupt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void broadcastNewProcess() {
        for (ProcessInfo processInfo : otherProcesses) {
            sendMessageToProcess(processInfo, new Message(id, MessageType.NEW_PROCESS, processInfo.getId()));
        }
    }

    private void requestCoordinatorElection() {
        startElectionProcess();
    }

    private void startElectionProcess() {
        isElectionInProgress.set(true);
        electionStartTime = System.currentTimeMillis();

        for (ProcessInfo processInfo : otherProcesses) {
            if (processInfo.getId() > id) {
                sendMessageToProcess(processInfo, new Message(id, MessageType.ELECTION, processInfo.getId()));
            }
        }

        launchElectionTimeoutMonitor();
    }

    private void processIncomingMessages() throws SocketException {
        try (
            Socket socket = serverSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String messageString = in.readLine();
            Message message = Message.fromString(messageString);

            logArea.append("Process " + id + " received: " + message.toLogString() + "\n");
            switch (message.getType()) {
                case NEW_PROCESS:
                    processNewProcessMessage(message);
                    break;
                case ELECTION:
                    processElectionMessage(message);
                    break;
                case COORDINATOR:
                    processCoordinatorMessage(message);
                    break;
                case OK:
                    processOkMessage(message);
                    break;
                case COORDINATOR_ALIVE:
                    processCoordinatorAliveMessage(message);
                    break;
                case STOP:
                    processStopMessage(message);
                    break;
                case COORDINATOR_STOP:
                    processCoordinatorStopMessage(message);
                    break;
            }
        } catch (SocketTimeoutException e) {
            if (!isCoordinator) {
                verifyCoordinatorLiveness();
            }
        } catch (SocketException e) {
            throw e;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processNewProcessMessage(Message message) {
        if (!processExists(message.getSenderId())) {
            otherProcesses.add(new ProcessInfo(message.getSenderId(), PORT_BASE + message.getSenderId()));
        }
    }

    private void processElectionMessage(Message message) {
        if (!isElectionInProgress.get()) {
            startElectionProcess();
        }

        sendMessageToProcess(new ProcessInfo(message.getSenderId(), PORT_BASE + message.getSenderId()),
                new Message(id, MessageType.OK, message.getSenderId()));
    }

    private void processCoordinatorMessage(Message message) {
        if (isElectionInProgress.get()) {
            return;
        }
        isElectionInProgress.set(false);
        if (message.getTimestamp() >= latestCoordinatorTimestamp  && message.getSenderId() > id) {
            latestCoordinatorTimestamp = message.getTimestamp();
            coordinatorId = message.getSenderId();
        }
        terminateHeartbeatThread();
        isCoordinator = false;
    }

    private void processOkMessage(Message message) {
        isElectionInProgress.set(false);
    }

    private void processCoordinatorAliveMessage(Message message) {
        lastAliveMessageTime = System.currentTimeMillis();
        if (message.getSenderId() > id) {
            terminateHeartbeatThread();
            isCoordinator = false;
        }
    }

    private void processStopMessage(Message message) {
        removeProcessFromList(message.getSenderId());

        if (isElectionInProgress.get() && message.getSenderId() > id) {
            startElectionProcess();
        }
    }

    private void processCoordinatorStopMessage(Message message) {
        isCoordinator = false;
        coordinatorId = -1;

        removeProcessFromList(message.getSenderId());

        if (isElectionInProgress.get()) {
            startElectionProcess();
        }
    }

    private void removeProcessFromList(int processId) {
        for (ProcessInfo processInfo : otherProcesses) {
            if (processInfo.getId() == processId) {
                otherProcesses.remove(processInfo);
                break;
            }
        }
    }

    private void verifyCoordinatorLiveness() {
        if (System.currentTimeMillis() - lastAliveMessageTime > COORDINATOR_TIMEOUT_IN_MS) {
            startElectionProcess();
        }
    }

    private void launchElectionTimeoutMonitor() {
        electionTimeoutThread = new Thread(() -> {
            while (!electionTimeoutThread.isInterrupted()) {
                try {
                    Thread.sleep(ELECTION_TIMEOUT_IN_MS);
                    if (isElectionTimeoutExceeded() && !isCoordinator) {
                        declareSelfAsCoordinator();
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        electionTimeoutThread.start();
    }

    private boolean isElectionTimeoutExceeded() {
        return isElectionInProgress.get() && System.currentTimeMillis() - electionStartTime > ELECTION_TIMEOUT_IN_MS;
    }

    private void declareSelfAsCoordinator() {
        for (ProcessInfo processInfo : otherProcesses) {
            sendMessageToProcess (processInfo, new Message(id, MessageType.COORDINATOR, processInfo.getId()));
        }

        isCoordinator = true;
        coordinatorId = id;
        isElectionInProgress.set(false);
        startCoordinatorHeartbeatThread();
    }

    private void startCoordinatorHeartbeatThread() {
        coordinatorHeartbeatThread = new Thread(() -> {
            while (!coordinatorHeartbeatThread.isInterrupted() && isCoordinator) {
                try {
                    Thread.sleep(ALIVE_MESSAGE_INTERVAL_IN_MS);
                    logArea.append("Process " + id + " sending alive message to other processes.\n");
                    broadcastCoordinatorAliveSignal();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        coordinatorHeartbeatThread.start();
    }

    private void broadcastCoordinatorAliveSignal() throws InterruptedException {
        for (ProcessInfo processInfo : otherProcesses) {
            sendMessageToProcess (processInfo, new Message(id, MessageType.COORDINATOR_ALIVE, processInfo.getId()));
        }

    }

    private void sendMessageToProcess (ProcessInfo receiver, Message message) {
        try (Socket socket = new Socket("localhost", receiver.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(message.toString());
        } catch (IOException e) {
        }
    }

    private boolean processExists(int processId) {
        return otherProcesses.stream().anyMatch(processInfo -> processInfo.getId() == processId);
    }

    private void terminateHeartbeatThread () {
        if (coordinatorHeartbeatThread != null) {
            coordinatorHeartbeatThread.interrupt();
            try {
                coordinatorHeartbeatThread.join();
            } catch (InterruptedException e) {

            }
        }
    }


}
