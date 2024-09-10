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
    protected static final int ELECTION_TIMEOUT_IN_MS = 2000;

    protected int id;
    protected int port;
    protected boolean isCoordinator;
    protected int coordinatorId;
    protected AtomicBoolean isElectionInProgress;
    protected long electionStartTime;
    protected long lastAliveMessageTime;
    protected long lastOkMessageTime;

    protected List<ProcessInfo> otherProcesses;
    protected ServerSocket serverSocket;
    protected AtomicBoolean isRunning; // To control the process loop
    protected AtomicBoolean foundEligibleProcess;

    private static final Logger LOGGER = Logger.getLogger(java.lang.Process.class.getName());

    public Process(int id) {
        this.id = id;
        this.port = PORT_BASE + id;
        this.isCoordinator = false;
        this.coordinatorId = -1; // Initially, no coordinator
        this.otherProcesses = new ArrayList<>();

        this.lastAliveMessageTime = System.currentTimeMillis();
        this.isElectionInProgress = new AtomicBoolean(false);
        this.isRunning = new AtomicBoolean(true);
        this.foundEligibleProcess = new AtomicBoolean(false);

    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);

            if (!isCoordinator) {
                findCoordinator();
            }

            while (isRunning.get()) {
                handleIncomingMessages();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in process " + id + ": " + e.getMessage(), e);
        }
    }

    private void handleIncomingMessages() {
        try {
            configureServerSocketTimeout();

            Socket clientSocket = serverSocket.accept(); //Handle closed Exception
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                String message = in.readLine();

                handleMessage(message);
                clientSocket.close();
            }
        } catch (SocketTimeoutException e) {
            performPeriodicTasks();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error handling incoming message in process " + id + ": " + e.getMessage(), e);
        }
    }

    // Perform periodic tasks like coordinator heartbeats and election timeouts
    private void performPeriodicTasks() {
        if (!isCoordinator && checkLastAliveMessageTime()) {
            findCoordinator();
        }

        if (isCoordinator) {
            sendCoordinatorHeartbeatIfNeeded();
        }

        if (checkElectionTimeout()) {
            declareCoordinator();
        }
    }

    private void sendCoordinatorHeartbeatIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAliveMessageTime >= COORDINATOR_TIMEOUT_IN_MS / 2) {
            sendCoordinatorAliveMessage();
            lastAliveMessageTime = currentTime;
        }
    }

    // Helper to set server socket timeout based on process role
    private void configureServerSocketTimeout() throws SocketException {
        if (isCoordinator) {
            serverSocket.setSoTimeout(COORDINATOR_TIMEOUT_IN_MS / 2);
        } else {
            serverSocket.setSoTimeout(COORDINATOR_TIMEOUT_IN_MS * 2);
        }
    }

    private void sendCoordinatorAliveMessage() {
        for (ProcessInfo otherProcess : otherProcesses) {
            Message message = new Message(id, MessageType.COORDINATOR_ALIVE, otherProcess.getId());
            sendMessage(otherProcess, message);
        }
        LOGGER.log(Level.INFO, "Process " + id + " sent COORDINATOR_ALIVE message to other processes.");
    }

    public void handleMessage(String message) {
        Message receivedMessage = Message.fromString(message);
        if (receivedMessage == null) {
            return;
        }

        switch (receivedMessage.getType()) {
            case ELECTION:
                handleElectionMessage(receivedMessage.getSenderId());
                break;
            case OK:
                handleOkMessage(receivedMessage.getSenderId());
                break;
            case COORDINATOR:
                handleCoordinatorMessage(receivedMessage.getSenderId());
                break;
            case COORDINATOR_ALIVE:
                handleCoordinatorAliveMessage(receivedMessage.getSenderId());
                break;
            case STOP:
                removeProcess(receivedMessage.getSenderId());
                break;
        }
    }

    private void handleOkMessage(int senderId) {
        if (senderId > id) {
            isElectionInProgress.set(false);
            foundEligibleProcess.set(true);
            LOGGER.log(Level.INFO, "Process " + id + " received OK from a higher ID. Stopping message sending.");
        }
    }

    private void handleCoordinatorMessage(int senderId) {
        if (senderId < id) {
            return; // Ignore COORDINATOR messages from lower IDs during an election
        }
        coordinatorId = senderId;
        isElectionInProgress.set(false);
        isCoordinator = (id == coordinatorId);
        foundEligibleProcess.set(false); // Reset if a new coordinator is elected
        LOGGER.log(Level.INFO, "Process " + id + " acknowledges new coordinator: " + coordinatorId);
    }

    private void handleCoordinatorAliveMessage(int senderId) {
        if (senderId < id) {
            return; // Ignore COORDINATOR_ALIVE messages from lower IDs during an election
        }
        coordinatorId = senderId;
        isElectionInProgress.set(false);
        isCoordinator = (id == coordinatorId);
        foundEligibleProcess.set(false); // Reset if coordinator is alive
        LOGGER.log(Level.INFO, "Process " + id + " received COORDINATOR_ALIVE from coordinator " + coordinatorId);
    }

    private void removeProcess(int processId) {
        otherProcesses.removeIf(process -> process.getId() == processId);
        if (coordinatorId == processId) {
            coordinatorId = -1;
            isCoordinator = false;
            foundEligibleProcess.set(false);
            LOGGER.log(Level.INFO, "Coordinator died. Resuming message sending.");
        }
        LOGGER.log(Level.INFO, "Process " + id + " removed process " + processId + " from the list of processes.");
    }

    public void sendMessage(ProcessInfo receiver, Message message) {
        if (foundEligibleProcess.get()) {
            LOGGER.log(Level.INFO, "Process " + id + " is in an election. Cannot send message to process " + receiver.getId());
            return;
        }
        try (Socket socket = new Socket("localhost", receiver.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(message.toString());
            LOGGER.log(Level.INFO, "Process " + id + " sent message to process " + receiver.getId() + ": " + message.getType());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending message to process " + receiver.getId() + ": " + e.getMessage(), e);
        }
    }


    public void stop() {
        try {
            isRunning.set(false);

            for (ProcessInfo otherProcess : otherProcesses) {
                sendMessage(otherProcess, new Message(id, MessageType.STOP, otherProcess.getId()));
            }

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                LOGGER.log(Level.INFO, "Process " + id + " stopped.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error stopping process " + id + ": " + e.getMessage(), e);
        }
    }

    private void initiateElection() {
        if (!isElectionInProgress.get()) {
            isElectionInProgress.set(true);
            electionStartTime = System.currentTimeMillis();
            LOGGER.log(Level.INFO, "Process " + id + " initiated an election.");

            for (ProcessInfo otherProcess : otherProcesses) {
                if (otherProcess.getId() > id) {
                    sendMessage(otherProcess, new Message(id, MessageType.ELECTION, otherProcess.getId()));
                }
            }
        }
    }

    private void findCoordinator() {
        LOGGER.log(Level.INFO, "Process " + id + " is looking for the coordinator.");

        for (ProcessInfo otherProcess : otherProcesses) {
            try (Socket socket = new Socket("localhost", otherProcess.getPort())) {
                sendMessage(otherProcess, new Message(id, MessageType.ELECTION, otherProcess.getId()));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Process " + id + " failed to connect to process " + otherProcess.getId(), e);
            }
        }

        if (coordinatorId == -1) {
            initiateElection();
        }
    }

    private void declareCoordinator() {
        if (isElectionInProgress.get() && !foundEligibleProcess.get()) {
            LOGGER.log(Level.INFO, "Process " + id + " is the new coordinator.");
            isCoordinator = true;
            coordinatorId = id;
            isElectionInProgress.set(false);

            for (ProcessInfo otherProcess : otherProcesses) {
                sendMessage(otherProcess, new Message(id, MessageType.COORDINATOR, otherProcess.getId()));
            }
        }
    }

    private void handleElectionMessage(int senderId) {
        if (!isElectionInProgress.get() || senderId > coordinatorId) {
            isElectionInProgress.set(true);
            electionStartTime = System.currentTimeMillis();

            if (senderId > id) {
                sendMessage(new ProcessInfo(senderId, PORT_BASE + senderId), new Message(id, MessageType.OK, senderId));
            }

            initiateElection();
        }
    }

    private boolean checkElectionTimeout() {
        return isElectionInProgress.get()
                && (System.currentTimeMillis() - electionStartTime) > ELECTION_TIMEOUT_IN_MS
                && !foundEligibleProcess.get();
    }

    private boolean checkLastAliveMessageTime() {
        return (System.currentTimeMillis() - lastAliveMessageTime) > COORDINATOR_TIMEOUT_IN_MS;
    }
}
