package org.kuleuven.engineering.dataReading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.kuleuven.engineering.*;
import org.kuleuven.engineering.graph.Graph;
import org.kuleuven.engineering.graph.GraphNode;
import org.kuleuven.engineering.types.Location;
import org.kuleuven.engineering.types.Stack;
import org.kuleuven.engineering.types.Bufferpoint;
import org.kuleuven.engineering.types.Vehicle;
import org.kuleuven.engineering.types.Request;

public class DataReader {
    public static Warehouse read(String filePath) {
        try {
            String content = Files.readString(Path.of(filePath));
            JSONObject object = JsonParser.parseString(content);

            int loadingDuration = object.getInt("loadingduration");
            int vehicleSpeed = object.getInt("vehiclespeed");
            int stackCapacity = object.getInt("stackcapacity");

            List<Map<String, Object>> Jstacks = JsonParser.toList(object.getJSONArray("stacks"));
            List<Map<String, Object>> Jbufferpoints = JsonParser.toList(object.getJSONArray("bufferpoints"));
            List<Map<String, Object>> Jvehicles = JsonParser.toList(object.getJSONArray("vehicles"));
            List<Map<String, Object>> Jrequests = JsonParser.toList(object.getJSONArray("requests"));

            Graph graph = new Graph(vehicleSpeed);
            HashMap<String, GraphNode> nodeMap = new HashMap<>();

            for (Map<String, Object> Jobject : Jstacks) {
                Stack stack = new Stack(new JSONObject(Jobject), stackCapacity);
                Location location = new Location((int) Jobject.get("x"), (int) Jobject.get("y"));
                GraphNode node = new GraphNode(stack, location);
                graph.addNode(node);
                nodeMap.put(node.getName(), node);
            }

            for (Map<String, Object> Jobject : Jbufferpoints) {
                Bufferpoint bufferpoint = new Bufferpoint(new JSONObject(Jobject));
                Location location = new Location((int) Jobject.get("x"), (int) Jobject.get("y"));
                GraphNode node = new GraphNode(bufferpoint, location);
                graph.addNode(node);
                nodeMap.put(node.getName(), node);
            }

            List<Vehicle> vehicles = new ArrayList<>();
            for (Map<String, Object> Jobject : Jvehicles) {
                vehicles.add(new Vehicle(new JSONObject(Jobject)));
            }

            List<Request> requests = new ArrayList<>();
            for (Map<String, Object> Jobject : Jrequests) {
                GraphNode pickupLocation, placeLocation;
                JSONObject R_object = new JSONObject(Jobject);
                // System.out.println(R_object.getJSONArray("pickupLocation").getString(0)+"  "+R_object.getJSONArray("placeLocation").getString(0));
                try{
                    pickupLocation = nodeMap.get(R_object.getJSONArray("pickupLocation").getString(0));
                    placeLocation = nodeMap.get(R_object.getJSONArray("placeLocation").getString(0));
                } catch (JSONException e){
                    pickupLocation = nodeMap.get(R_object.getString("pickupLocation"));
                    placeLocation = nodeMap.get(R_object.getString("placeLocation"));
                }

                int ID = R_object.getInt("ID");
                String boxID = R_object.getString("boxID");
                requests.add(new Request(pickupLocation, placeLocation, ID, boxID));
            }

            return new Warehouse(graph, vehicles, requests, loadingDuration);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
