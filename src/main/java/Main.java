import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {

    public static void main(String[] args) {
        int numberOfProcesses = 9; // You can change this to simulate with more or fewer processes

        List<Process> processes = new ArrayList<>();

        for (int i = 0; i < numberOfProcesses; i++) {
            Process process = new Process(i);
            processes.add(process);
        }

        for (int i = 0; i < numberOfProcesses; i++) {
            for (int j = 0; j < numberOfProcesses; j++) {
                if (i != j) {
                    ProcessInfo otherProcess = new ProcessInfo(j, Process.PORT_BASE + j);
                    processes.get(i).otherProcesses.add(otherProcess);
                }
            }
        }

        for (Process process : processes) {
            new Thread(process::start).start();
        }

        try {
            Thread.sleep(10000); // Wait for 10 seconds to allow election and coordinator declaration
            System.out.println("Simulating coordinator failure...");

            processes.getLast().stop();

            Thread.sleep(100000); // Wait some time to see if a new election occurs
            System.out.println("Test finished.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}


