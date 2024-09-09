import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class Process {
    protected static final int PORT_BASE = 5000;
    protected static final int COORDINATOR_TIMEOUT_IN_MS = 3000;
    protected static final int ELECTION_TIMEOUT_IN_MS = 2000;

    protected int id;
    protected int port;
    protected boolean isCoordinator;
    protected int coordinatorId;
    protected boolean isElectionInProgress;
    protected long electionStartTime;

    protected List<ProcessInfo> otherProcesses;

    protected ServerSocket serverSocket;


    public Process(int id) {
        this.id = id;
        this.port = PORT_BASE + id;
        this.isCoordinator = false;
        this.coordinatorId = -1; // Initially, no coordinator
        this.otherProcesses = new ArrayList<>();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);

            if (!isCoordinator) {
                findCoordinator();
            }

            while (true) {
                if (serverSocket.isClosed()) {
                    break;
                }

                Socket clientSocket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String message = in.readLine();

                handleMessage(message);

                clientSocket.close();

                if (isElectionInProgress && System.currentTimeMillis() - electionStartTime > ELECTION_TIMEOUT_IN_MS) { //TODO: put in separate method
                    declareCoordinator();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(ProcessInfo receiver, Message message) {
        try {
            Socket socket = new Socket("localhost", receiver.getPort());
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(message.toString());
            socket.close();
        } catch (IOException e) {
            System.err.println("Error sending message to process " + receiver.getId() + ": " + e.getMessage());
        }
    }

    public void handleMessage(String message) {
        Message receivedMessage = Message.fromString(message);
        if (receivedMessage == null) {
            return;
        }

        switch (receivedMessage.getType()) {
            case ELECTION:
                handleElectionMessage();
                break;
            case OK:
                // Stop the election timer if an OK is received from a higher ID process
                if (receivedMessage.getSenderId() > id) {
                    isElectionInProgress = false;
                }
                break;
            case COORDINATOR:
                // Update the coordinator ID
                coordinatorId = receivedMessage.getSenderId();
                isElectionInProgress = false;
                System.out.println("Process " + id + " acknowledges new coordinator: " + coordinatorId);
                break;
            case COORDINATOR_ALIVE:
                // Coordinator is alive, reset the coordinator timeout
                coordinatorId = receivedMessage.getSenderId();
                break;
            default:
                System.err.println("Unknown message type: " + receivedMessage.getType());
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("Process " + id + " stopped.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initiateElection() {
        isElectionInProgress = true;
        electionStartTime = System.currentTimeMillis();
        for (ProcessInfo otherProcess : otherProcesses) {
            if (otherProcess.getId() > id) {
                Message electionMessage = new Message(id, MessageType.ELECTION, otherProcess.getId());
                sendMessage(otherProcess, electionMessage);
            }
        }
    }

    private void findCoordinator() {
        // If other processes are already known, try to connect to them
        for (ProcessInfo otherProcess : otherProcesses) {
            try {
                Socket socket = new Socket("localhost", otherProcess.getPort());
                Message message = new Message(id, MessageType.ELECTION, otherProcess.getId());
                sendMessage(otherProcess, message);
                socket.close();
            } catch (IOException e) {
                // If connection fails, assume the process is down
                System.err.println("Failed to connect to process " + otherProcess.getId());
                // You might want to remove this process from otherProcesses
            }
        }

        // If no successful connections, declare itself the coordinator
        if (coordinatorId == -1) {
            declareCoordinator();
        }
    }

    private void declareCoordinator() {
        if (isElectionInProgress) {
            System.out.println("Process " + id + " is the new coordinator.");
            isCoordinator = true;
            coordinatorId = id;
            isElectionInProgress = false;

            // Notify other processes about the new coordinator
            for (ProcessInfo otherProcess : otherProcesses) {
                Message message = new Message(id, MessageType.COORDINATOR, otherProcess.getId());
                sendMessage(otherProcess, message);
            }
        }
    }

    private void handleElectionMessage() {
        if (!isElectionInProgress) {
            isElectionInProgress = true;
            electionStartTime = System.currentTimeMillis();

            // Send OK messages to lower ID processes
            for (ProcessInfo otherProcess : otherProcesses) {
                if (otherProcess.getId() < id) {
                    Message okMessage = new Message(id, MessageType.OK, otherProcess.getId());
                    sendMessage(otherProcess, okMessage);
                }
            }

            initiateElection();
        }
    }
}
