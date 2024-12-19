package org.kuleuven.engineering;

import org.kuleuven.engineering.dataReading.DataReader;

import java.io.File;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        String fileName = args[0];
        File f = new File(fileName);
        if(!f.exists()) throw new Exception("File doesn't exist");
        Warehouse warehouse = DataReader.read(f.getPath());
        warehouse.scheduleRequests();
        warehouse.writeOperationLog(args[1]);
    }
}

// bij ophalen van stack kijken of het needed box is, of box van andere request

