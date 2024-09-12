import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GUI {
    private JFrame frame;
    private JTextArea logArea;
    private JTextField batchField;
    private JButton startBatchButton;
    private JButton addProcessButton;
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
                int processCount = processes.size();
                Process process = new Process(processCount, logArea);

                for (Process p: processes) {
                    ProcessInfo otherProcess = new ProcessInfo(p.id, Process.PORT_BASE + p.id);
                    process.otherProcesses.add(otherProcess);
                }
                processes.add(process);

                addNewProcess(processCount);

                new Thread(process::start).start();
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
        startBatchButton.setEnabled(false);

        for (int i = 0; i < batchSize; i++) {
            Process process = new Process(i, logArea);
            processes.add(process);
        }

        for (int i = 0; i < batchSize; i++) {
            for (int j = 0; j < batchSize; j++) {
                if (i != j) {
                    ProcessInfo otherProcess = new ProcessInfo(j, Process.PORT_BASE + j);
                    processes.get(i).otherProcesses.add(otherProcess);
                }
            }
            addNewProcess(i);
        }

        for (int i = 0; i < batchSize; i++) {
            new Thread(processes.get(i)::start).start();
        }
    }

    private void addNewProcess(int processesCount) {

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
                stopProcess(processesCount, stopButton);
            }
        });

        processPanel.add(processEntry);
        processPanel.revalidate();
        processPanel.repaint();
    }

    private void stopProcess(int processId, JButton stopButton) {
        if (Objects.equals(stopButton.getText(), "Start")) {
            processes.get(processId).otherProcesses.clear();
            for (Process p : processes) {
                if (p.id == processId) {
                    continue;
                }
                ProcessInfo otherProcess = new ProcessInfo(p.id, Process.PORT_BASE + p.id);
                processes.get(processId).otherProcesses.add(otherProcess);
            }

            new Thread(processes.get(processId)::start).start();
            stopButton.setText("Stop");
        } else {
            processes.get(processId).stop();
            stopButton.setText("Start");
        }
    }
}
