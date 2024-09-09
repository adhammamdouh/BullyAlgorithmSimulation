public class Process {
    private static final int PORT_BASE = 5000; // Base port for processes
    private static final int COORDINATOR_TIMEOUT = 3000; // Coordinator timeout in milliseconds
    private static final int ELECTION_TIMEOUT = 2000; // Election timeout in milliseconds

    private int id;
    private int port;
    private boolean isCoordinator;
    private int coordinatorId;

    public Process(int id) {
        this.id = id;
        this.port = PORT_BASE + id;
        this.isCoordinator = false;
        this.coordinatorId = -1; // Initially, no coordinator
    }
}
