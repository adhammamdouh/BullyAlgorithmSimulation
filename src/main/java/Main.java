import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            GUI gui = new GUI();

            gui.startSocketListener();
        } else {
            startProcess(args);
        }
    }

    private static void startProcess(String[] args) {
        System.out.println("Starting process");
        System.out.println("Args: " + String.join(", ", args));
        int id = Integer.parseInt(args[0]);
        int centralLoggerPort = Integer.parseInt(args[1]);
        String otherProcessesStr = args[2];

        ProcessApp process = new ProcessApp(id, centralLoggerPort);

        List<String> otherProcessesList = List.of(otherProcessesStr.split(","));
        System.out.println("Other processes: " + otherProcessesList);
        System.out.println("Other processes size: " + otherProcessesList.size());
        for (int i = 0; i < otherProcessesList.size(); i++) {
            if (otherProcessesList.get(i).isEmpty()) {
                continue;
            }
            System.out.println("Item: " + otherProcessesList.get(i));
            String[] processInfo = otherProcessesList.get(i).split(":");
            process.otherProcesses.add(new ProcessInfo(Integer.parseInt(processInfo[0]), Integer.parseInt(processInfo[1])));
        }

        new Thread(process::start).start();
    }
}


