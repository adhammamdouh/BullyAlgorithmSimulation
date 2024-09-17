import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GUI {
    private JFrame frame;
    private JTextArea logArea;
    private JTextField batchField;
    private JButton startBatchButton;
    private JButton addProcessButton;
    private JPanel processPanel;
    private JScrollPane scrollPane;
    private List<JButton> stopButtons = new ArrayList<>();

    private ServerSocket serverSocket;
    private int port = 4999;

    private List<ProcessApp> processApps = new ArrayList<>();
    private List<Process> processes = new ArrayList<>();
    private List<Boolean> enabled = new ArrayList<>();

    private AtomicInteger processesIdentifier = new AtomicInteger(0);

    public GUI() {
        frame = new JFrame("Bully Algorithm Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // Top Panel for batch size and start button
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Batch Size:"));
        batchField = new JTextField(5);
        topPanel.add(batchField);
        startBatchButton = new JButton("Start Batch");
        topPanel.add(startBatchButton);
        frame.add(topPanel, BorderLayout.NORTH);

        // Process Panel
        processPanel = new JPanel();
        processPanel.setLayout(new BoxLayout(processPanel, BoxLayout.Y_AXIS));
        processPanel.setBorder(new TitledBorder("Processes"));

        // Left Panel for logs
        logArea = new JTextArea();
        logArea.setEditable(false);
        //logArea.setEnabled(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        frame.add(logScrollPane, BorderLayout.CENTER);

        // Make it scrollable
        scrollPane = new JScrollPane(processPanel);
        scrollPane.setPreferredSize(new Dimension(200, 0));
        frame.add(scrollPane, BorderLayout.EAST);

        // Bottom Panel for Add Process and Reset buttons
        JPanel bottomPanel = new JPanel();
        addProcessButton = new JButton("Add New Process");
        bottomPanel.add(addProcessButton);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Button Actions
        startBatchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startBatchProcesses();
            }
        });

        addProcessButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addProcessButton.setEnabled(false);
                int id = processesIdentifier.incrementAndGet();

                StringBuilder otherProcessesArg = new StringBuilder();

                for (int i = 0; i < processApps.size(); i++) {
                    if (!enabled.get(i)) continue;
                    otherProcessesArg.append(processApps.get(i).id).append(":").append(processApps.get(i).port).append(",");
                }

                ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp", "target/classes", "Main", String.valueOf(id), String.valueOf(port), otherProcessesArg.toString());
                ProcessApp addedProcess = new ProcessApp(id, ProcessApp.PORT_BASE + id);

                // 2. Start the new process
                try {
                    processBuilder.inheritIO();
                    Process process = processBuilder.start();
                    processes.add(process);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                processApps.add(addedProcess);
                enabled.add(true);
                addProcessButton.setEnabled(true);
                addNewProcess(id);
            }
        });

        frame.setVisible(true);
    }

    private void startBatchProcesses() {
        int batchSize;

        // Validate batch size input
        try {
            batchSize = Integer.parseInt(batchField.getText());
            if (batchSize <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Please enter a valid batch size", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        startBatchButton.setEnabled(false);

        List<ProcessApp> currentBatchProcesses = new ArrayList<>();

        for (int i = 0; i < batchSize; i++) {
            ProcessApp process = new ProcessApp(processesIdentifier.incrementAndGet(), port);
            currentBatchProcesses.add(process);
            processApps.add(process);
            enabled.add(true);
        }

        // Set up other processes information for each process
        for (int i = 0; i < batchSize; i++) {
            StringBuilder otherProcessesArg = new StringBuilder();

            for (int j = 0; j < processApps.size(); j++) {
                if (!enabled.get(j) || processApps.get(j).id == currentBatchProcesses.get(i).id) continue;
                otherProcessesArg.append(processApps.get(j).id).append(":").append(processApps.get(j).port).append(",");
            }

            ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp", "target/classes", "Main", String.valueOf(currentBatchProcesses.get(i).id), String.valueOf(port), otherProcessesArg.toString());

            try {
                processBuilder.inheritIO();
                Process process = processBuilder.start();
                processes.add(process);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            addNewProcess(currentBatchProcesses.get(i).id);
        }

        startBatchButton.setEnabled(true);
    }

    private void addNewProcess(int processId) {

        JPanel processEntry = new JPanel();
        processEntry.setLayout(new FlowLayout(FlowLayout.LEFT));

        JLabel processLabel = new JLabel("Process " + processId);
        JButton stopButton = new JButton("Stop");
        stopButtons.add(stopButton);

        processEntry.add(processLabel);
        processEntry.add(stopButton);

        // Action for stopping the process
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopProcess(processId, stopButton);
            }
        });

        processPanel.add(processEntry);
        processPanel.revalidate();
        processPanel.repaint();
    }

    private void stopProcess(int processId, JButton stopButton) {
        for (int i = 0; i < processApps.size(); i++) {
            if (processApps.get(i).id == processId) {
                stopButton.setEnabled(false);
                logArea.append("Server: Stopping process " + processId + "\n");
                sendStopSignal(processApps.get(i).id , processApps.get(i).port);
                break;
            }
        }
    }

    public void startSocketListener() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Server: Server socket started on port " + port);
                logArea.append("Server: Server socket started on port " + port + "\n");
                while (true) {
                    try (Socket socket = serverSocket.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                        String messageString = in.readLine();
                        if (messageString.startsWith("exit:")) {
                            int id = Integer.parseInt(messageString.split(":")[1].trim());
                            for (int i = 0; i < processApps.size(); i++) {
                                if (processApps.get(i).id == id) {
                                    processes.get(i).destroy();
                                    enabled.set(i, false);
                                    processPanel.remove(stopButtons.get(i).getParent());
                                    processPanel.revalidate();
                                    processPanel.repaint();
                                    break;
                                }
                            }
                            logArea.append("Server: Process " + id + " fully stopped\n");
                            continue;
                        }
                        logArea.append(messageString + "\n");
                    }
                }
            } catch (SocketException e) {
                logArea.append("Server: Socket closed\n");
            } catch (SocketTimeoutException e) {
                logArea.append("Server: Socket timeout\n");
            } catch (IOException e) {
                logArea.append("Server: Error reading from socket\n");
            } finally {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        logArea.append("Server: Error closing the server socket\n");
                    }
                }
            }
        }).start();
    }

    private void sendStopSignal(int id, int processPort) {
        try (Socket socket = new Socket("localhost", processPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            Message message = new Message(0, MessageType.FORCE_STOP, id);
            out.println(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
