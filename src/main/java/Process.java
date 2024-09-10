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

    protected List<ProcessInfo> otherProcesses;
    protected ServerSocket serverSocket;
    protected AtomicBoolean isRunning; // To control the process loop

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

    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);

            if (!isCoordinator) {
                findCoordinator();
            }

            Socket clientSocket = null;
            while (isRunning.get()) {
                try {
                    if (isCoordinator) {
                        serverSocket.setSoTimeout(COORDINATOR_TIMEOUT_IN_MS / 2);
                    } else {
                        serverSocket.setSoTimeout(COORDINATOR_TIMEOUT_IN_MS * 2);
                    }

                    clientSocket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                    String message = in.readLine();

                    handleMessage(message);
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, "Process " + id + " timed out." + " " + !isCoordinator + " " + checkLastAliveMessageTime());
                    if (!isCoordinator && checkLastAliveMessageTime()) {
                        findCoordinator();
                    }

                    if (isCoordinator && isRunning.get()) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastAliveMessageTime >= COORDINATOR_TIMEOUT_IN_MS / 2) {
                            sendCoordinatorAliveMessage();
                            lastAliveMessageTime = currentTime;
                        }
                    }
                }
            }
            clientSocket.close();
        } catch (SocketTimeoutException e) {
            LOGGER.log(Level.INFO, "Process " + id + " timed out.");
        } catch (SocketException e) {
            LOGGER.log(Level.SEVERE, "Error in process " + id + ": " + e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error in process " + id + ": " + e.getMessage(), e);
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
                if (receivedMessage.getSenderId() > id) {
                    isElectionInProgress.set(false);
                }
                LOGGER.log(Level.INFO, "Process " + id + " received OK message from process " + receivedMessage.getSenderId());
                break;
            case COORDINATOR:
                coordinatorId = receivedMessage.getSenderId();
                isElectionInProgress.set(false);
                isCoordinator = (id == coordinatorId);
                LOGGER.log(Level.INFO, "Process " + id + " acknowledges new coordinator: " + coordinatorId);
                break;
            case COORDINATOR_ALIVE:
                coordinatorId = receivedMessage.getSenderId();
                LOGGER.log(Level.INFO, "Process " + id + " received COORDINATOR_ALIVE from coordinator " + coordinatorId);
                break;
            case STOP:
                removeProcess(receivedMessage.getSenderId());
                break;

        }
    }

    public void sendMessage(ProcessInfo receiver, Message message) {
        try {
            Socket socket = new Socket("localhost", receiver.getPort());
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(message.toString());
            socket.close();
            LOGGER.log(Level.INFO, "Process " + id + " sent message to process " + receiver.getId() + ": " + message.getType());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending message to process " + receiver.getId() + ": " + e.getMessage(), e);
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {

                for (ProcessInfo otherProcess : otherProcesses) {
                    Message message = new Message(id, MessageType.STOP, otherProcess.getId());
                    sendMessage(otherProcess, message);
                }

                serverSocket.close();
                isRunning.set(false);
                LOGGER.log(Level.INFO, "Process " + id + " stopped.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error stopping process " + id + ": " + e.getMessage(), e);
        }
    }

    private void initiateElection() {
        if (!isElectionInProgress.get()) { // Start election only if not already in one
            isElectionInProgress.set(true);
            electionStartTime = System.currentTimeMillis();
            LOGGER.log(Level.INFO, "Process " + id + " initiated an election.");

            for (ProcessInfo otherProcess : otherProcesses) {
                if (otherProcess.getId() > id) {
                    Message electionMessage = new Message(id, MessageType.ELECTION, otherProcess.getId());
                    sendMessage(otherProcess, electionMessage);
                }
            }
        }
    }

    private void findCoordinator() {
        LOGGER.log(Level.INFO, "Process " + id + " is looking for the coordinator.");
        for (ProcessInfo otherProcess : otherProcesses) {
            try {
                Socket socket = new Socket("localhost", otherProcess.getPort());
                Message message = new Message(id, MessageType.ELECTION, otherProcess.getId());
                sendMessage(otherProcess, message);
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Process " + id + " failed to connect to process " + otherProcess.getId(), e);
            }
        }

        if (otherProcesses == null || otherProcesses.isEmpty()) {
            LOGGER.log(Level.INFO, "Process " + id + " is the new coordinator.");
            isCoordinator = true;
            coordinatorId = id;
        }

        if (coordinatorId == -1) {
            declareCoordinator();
        }
    }

    private void declareCoordinator() {
        // Only declare yourself the coordinator if an election is in progress and you haven't received any OK
        if (isElectionInProgress.get()) {
            LOGGER.log(Level.INFO, "Process " + id + " is the new coordinator.");
            isCoordinator = true;
            coordinatorId = id;
            isElectionInProgress.set(false);

            for (ProcessInfo otherProcess : otherProcesses) {
                Message message = new Message(id, MessageType.COORDINATOR, otherProcess.getId());
                sendMessage(otherProcess, message);
            }
        }
    }

    private void handleElectionMessage(int senderId) {
        LOGGER.log(Level.INFO, "Process " + id + " received an ELECTION message from (" + senderId + ").");

        // If you are not in an election or the sender has a higher ID than your current coordinator
        if (!isElectionInProgress.get() || senderId > coordinatorId) {
            isElectionInProgress.set(true);
            electionStartTime = System.currentTimeMillis();

            // Send OK only if the sender has a higher ID
            if (senderId < id) {
                Message okMessage = new Message(id, MessageType.OK, senderId);
                sendMessage(new ProcessInfo(senderId, PORT_BASE + senderId), okMessage);
            }

            initiateElection();
        }
    }

    private boolean checkElectionTimeout() {
        return isElectionInProgress.get() && (System.currentTimeMillis() - electionStartTime) > ELECTION_TIMEOUT_IN_MS;
    }

    private void removeProcess(int processId) {
        otherProcesses.removeIf(process -> process.getId() == processId);
        if (coordinatorId == processId) {
            coordinatorId = -1;
            isCoordinator = false;
        }
        LOGGER.log(Level.INFO, "Process " + id + " removed process " + processId + " from the list of processes.");
    }

    private boolean checkLastAliveMessageTime() {
        return (System.currentTimeMillis() - lastAliveMessageTime) > COORDINATOR_TIMEOUT_IN_MS;
    }
}
