package org.kuleuven.engineering;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner; 

import org.kuleuven.engineering.dataReading.DataReader;

public class TestWarehouse {
    public static void main(String[] args) {
        List<File> files = Arrays.asList(new File("./juist/data/").listFiles());
        Scanner scanner = new Scanner(System.in);
        for (File f : files) {
            System.out.printf("\033[33mRunning file %s!\033[0m %n", f.getName());
            Warehouse warehouse = DataReader.read(f.getPath());
            warehouse.scheduleRequests();
            warehouse.writeOperationLog("./output.txt");

            if (files.indexOf(f) != files.size()-1){
                System.out.print("Press enter to run next file with name: "+files.get(files.indexOf(f)+1).getName());
                String name = scanner.nextLine();
            }
        }
    }
}
