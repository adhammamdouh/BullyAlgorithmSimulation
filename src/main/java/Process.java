import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class Process implements Runnable {
    private static final int PORT_BASE = 5000; // Base port for processes
    private static final int COORDINATOR_TIMEOUT = 3000; // Coordinator timeout in milliseconds
    private static final int ELECTION_TIMEOUT = 2000; // Election timeout in milliseconds

    private int id;
    private int port;
    private boolean isCoordinator;
    private int coordinatorId;
    private ServerSocket serverSocket; // Server socket to listen for incoming messages

    public Process(int id) {
        this.id = id;
        this.port = PORT_BASE + id;
        this.isCoordinator = false;
        this.coordinatorId = -1; // Initially, no coordinator
    }

    @Override
    public void run() {
        while(true) {
            try {
                Socket clientSocket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String message = in.readLine();
                System.out.println("Process " + id + " received message: " + message);
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
