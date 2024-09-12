import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class GUI {
    private JFrame frame;
    private JTextArea logArea;
    private JTextField batchField;
    private JButton startBatchButton;
    private JButton addProcessButton;
    private JButton resetButton;
    private JPanel processPanel;
    private JScrollPane scrollPane;
    private List<JButton> stopButtons = new ArrayList<>();
    private List<Process> processes = new ArrayList<>();

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
        logArea.setEnabled(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        frame.add(logScrollPane, BorderLayout.CENTER);

        // Make it scrollable
        scrollPane = new JScrollPane(processPanel);
        scrollPane.setPreferredSize(new Dimension(200, 0));
        frame.add(scrollPane, BorderLayout.EAST);

        // Bottom Panel for Add Process and Reset buttons
        JPanel bottomPanel = new JPanel();
        addProcessButton = new JButton("Add New Process");
        resetButton = new JButton("Reset");
        bottomPanel.add(addProcessButton);
        bottomPanel.add(resetButton);
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
                addNewProcess();
            }
        });

        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetAll();
            }
        });

        frame.setVisible(true);
    }

    private void startBatchProcesses() {
        int batchSize;
        try {
            batchSize = Integer.parseInt(batchField.getText());
            if (batchSize <= 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Please enter a valid batch size", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        for (int i = 0; i < batchSize; i++) {
            addNewProcess();
        }
    }

    private void addNewProcess() {
        int processesCount = processes.size() + 1;
        System.out.println("Process " + processesCount + " added.");
        Process process = new Process(processesCount, logArea);
        processes.forEach(p -> process.otherProcesses.add(new ProcessInfo(p.id, Process.PORT_BASE + p.id)));

        processes.add(process);

        JPanel processEntry = new JPanel();
        processEntry.setLayout(new FlowLayout(FlowLayout.LEFT));

        JLabel processLabel = new JLabel("Process " + processesCount);
        JButton stopButton = new JButton("Stop");
        stopButtons.add(stopButton);

        processEntry.add(processLabel);
        processEntry.add(stopButton);

        // Action for stopping the process
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopProcess(processesCount);
            }
        });

        processPanel.add(processEntry);
        processPanel.revalidate();
        processPanel.repaint();

        logArea.append("Process " + processesCount + " added.\n");
        new Thread(process::start).start();
        logArea.append("Process " + processesCount + " started.\n");

        System.out.println("Process " + processesCount + " started.");
    }

    private void stopProcess(int processName) {
        logArea.append(processName + " stopped.\n");
        // Logic to stop the process can be implemented here
    }

    private void resetAll() {
        processPanel.removeAll();
        processPanel.revalidate();
        processPanel.repaint();
        batchField.setText("");
        logArea.setText("");
        processes.forEach(Process::stop);
        processes.clear();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GUI());
    }
}
