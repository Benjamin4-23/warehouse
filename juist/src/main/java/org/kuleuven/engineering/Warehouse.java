package org.kuleuven.engineering;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

import org.kuleuven.engineering.graph.Graph;
import org.kuleuven.engineering.graph.GraphNode;
import org.kuleuven.engineering.scheduling.BufferToStackSchedulingStrategy;
import org.kuleuven.engineering.scheduling.SchedulingStrategy;
import org.kuleuven.engineering.scheduling.StackToBufferSchedulingStrategy;
import org.kuleuven.engineering.scheduling.TopBoxSchedulingStrategy;
import org.kuleuven.engineering.types.Location;
import org.kuleuven.engineering.types.REQUEST_STATUS;
import org.kuleuven.engineering.types.Request;
import org.kuleuven.engineering.types.Stack;
import org.kuleuven.engineering.types.Vehicle;


public class Warehouse {
    private final Graph graph;
    private final List<Vehicle> vehicles;
    private final List<Request> requests;
    private final HashMap<Integer, Integer> stackIsUsedUntil;
    private final List<Integer[]> activeRelocations = new ArrayList<>();
    private final HashMap<Integer, Integer> waitForRequestFinish = new HashMap<>();
    private final List<String> operationLog = new ArrayList<>();
    private double currentTime = 0;
    private final int loadingSpeed;
    private int round = 0;
    private long startingTime;
    private final boolean[] firstGetAnother;
    private final List<List<Request>> requestsPerVehicleList;
    private final List<SchedulingStrategy> strategies;
    private final RequestDistribution requestDistributor;
    private final RequestHandling requestHandler;

    public Warehouse(Graph graph, List<Vehicle> vehicles, List<Request> requests, int loadingSpeed) {
        this.graph = graph;
        this.vehicles = vehicles;
        this.requests = requests;
        this.loadingSpeed = loadingSpeed;
        this.stackIsUsedUntil = new HashMap<>();
        for (GraphNode node : graph.getNodes()){
            if (node.getStorage() instanceof Stack stack){
                stackIsUsedUntil.put(stack.getID(), -1);
            }
        }
        firstGetAnother = new boolean[vehicles.size()];
        requestsPerVehicleList = new ArrayList<>();
        this.strategies = new ArrayList<>();
        requestHandler = new RequestHandling(loadingSpeed, this);
        requestDistributor = new RequestDistribution(vehicles, requestsPerVehicleList);
        initializeStrategies();
        
    }

    private void initializeStrategies() {
        // Add strategies in the order they should be executed
        strategies.add(new TopBoxSchedulingStrategy(requestDistributor, requestHandler, this));

        strategies.add(new StackToBufferSchedulingStrategy(requestDistributor, requestHandler, this));
            
        strategies.add(new BufferToStackSchedulingStrategy(requestDistributor, requestHandler, this));


   }

    public void scheduleRequests() {
        startingTime = System.currentTimeMillis();
        // Execute each strategy in order
        for (SchedulingStrategy strategy : strategies) {
            strategy.schedule();
            resetVehicleStackIDs();
            round++;
            System.out.println(currentTime);
        }
    }
    private void resetVehicleStackIDs() {
        for (Vehicle vehicle : vehicles){
            vehicle.resetStackIDs();
        }
    }
    
    













    
    





    public List<GraphNode> findNStorage(int N, GraphNode src, GraphNode dest, REQUEST_STATUS status, Vehicle currentVehicle){
        if(N == 0) return null;
        
        // check if current vehicle can go to one of its own stacks (to avoid interference with other vehicles)
        List<GraphNode> accessibleStacks = findOwnAccessibleStacks(currentVehicle, src, dest);
        if (!accessibleStacks.isEmpty()) return accessibleStacks;
        
        // find stack that no vehicle has requests for
        List<GraphNode> requestStacks = findAvailableStack(currentVehicle, src, dest, status);
        if (!requestStacks.isEmpty()) return requestStacks;

        // find stack that is in request of other vehicles
        List<GraphNode> remainingStacks = findOtherVehicleStacks(currentVehicle, src, dest, status);
        return remainingStacks;
    }

    private List<GraphNode> findOwnAccessibleStacks(Vehicle currentVehicle, GraphNode src, GraphNode dest) {
        List<GraphNode> accessibleStacks = new ArrayList<>();
        for (Integer stackID : currentVehicle.getMyStackIDs()) {
            int srcID = src.getStorage().getID();
            int destID = dest.getStorage().getID();
            boolean notSrcOrDest = stackID != srcID && stackID != destID;
            boolean notCurrentlyAtNode = currentVehicle.getCurrentNode() == null;
            boolean currentNodeIsStack = currentVehicle.getCurrentNode().getStorage() instanceof Stack;
            Stack currentNodeStack = currentNodeIsStack ? (Stack) currentVehicle.getCurrentNode().getStorage() : null;
            Stack stack2 = (Stack) graph.getStackByID(stackID).getStorage();

            
            if (notSrcOrDest && ( notCurrentlyAtNode || (currentNodeIsStack && currentNodeStack.getID() != stackID && !stack2.isFull()))) {
                int time1 = stackIsUsedUntil.get(stackID);
                int time2 = (int) currentTime;
                boolean stackIsUsed = time1 < time2;
                if (stackIsUsed) {
                    accessibleStacks.add(graph.getStackByID(stackID));
                }
            }
        }
        return accessibleStacks;
    }
    private List<GraphNode> findRequestStacks(Vehicle currentVehicle, GraphNode src, GraphNode dest, int type) {
        List<GraphNode> requestStacks = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (type == 2 && vehicle == currentVehicle) continue;
            for (Integer stackID : vehicle.getMyStackIDs()) {
                if (currentVehicle.getCurrentNode() == null || isDifferentStack(currentVehicle, stackID)) {
                    requestStacks.add(graph.getStackByID(stackID));
                }
            }
        }
        return requestStacks;
    }
    private boolean isDifferentStack(Vehicle currentVehicle, Integer stackID) {
        return currentVehicle.getCurrentNode().getStorage() instanceof Stack stack && stackID != stack.getID();
    }
    private boolean isValidNode(GraphNode node, GraphNode src, GraphNode dest, List<GraphNode> requestStacks) {
        return !(node == src || node == dest || requestStacks.contains(node)) && node.getStorage() instanceof Stack stack && !stack.isFull();
    }
    private List<GraphNode> findAvailableStack(Vehicle currentVehicle, GraphNode src, GraphNode dest, REQUEST_STATUS status) {
        // find stack that no vehicle has requests for
        List<GraphNode> requestStacks = findRequestStacks(currentVehicle, src, dest,1);

        // find stack that is closest to src or dest
        PriorityQueue<GraphNode> nodesByDistance = new PriorityQueue<>((node1, node2) -> {
            GraphNode node = (status == REQUEST_STATUS.SRC) ? src : dest;
            double distance1 = graph.calculateTime(node.getLocation(), node1.getLocation());
            double distance2 = graph.calculateTime(node.getLocation(), node2.getLocation());
            return Double.compare(distance1, distance2);
        });
        nodesByDistance.addAll(graph.getNodes());

        List<GraphNode> nodes = new ArrayList<>();
        for (GraphNode node : nodesByDistance) {
            if (isValidNode(node, src, dest, requestStacks)) {
                Stack stack = (Stack) node.getStorage();
                if ((stackIsUsedUntil.get(stack.getID()) < currentTime) || status == REQUEST_STATUS.SIMULATED) {
                    nodes.add(node);
                    return nodes;
                }
            }
        }
        return nodes; 
    }
    private List<GraphNode> findOtherVehicleStacks(Vehicle currentVehicle, GraphNode src, GraphNode dest, REQUEST_STATUS status) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphNode> requestStacks = findRequestStacks(currentVehicle, src, dest, 2);

        // find stack that is closest to src or dest
        PriorityQueue<GraphNode> nodesByDistance2 = new PriorityQueue<>((node1, node2) -> {
            GraphNode node = (status == REQUEST_STATUS.SRC) ? src : dest;
            double distance1 = graph.calculateTime(node.getLocation(), node1.getLocation());
            double distance2 = graph.calculateTime(node.getLocation(), node2.getLocation());
            return Double.compare(distance1, distance2);
        });
        nodesByDistance2.addAll(requestStacks);

        
        for (GraphNode node : nodesByDistance2){
            if(!(node == src || node == dest) && node.getStorage() instanceof Stack stack && !stack.isFull()){
                if (stackIsUsedUntil.get(stack.getID()) < currentTime){
                    nodes.add(node);
                    return nodes;
                }
            }
        }
        return nodes;
    }


    public HashMap<Integer, Integer> getStackIsUsedUntil() {
        return stackIsUsedUntil;
    }
    public HashMap<Integer, Integer> getWaitForRequestFinish() {
        return waitForRequestFinish;
    }
    public List<Integer[]> getActiveRelocations() {
        return activeRelocations;
    }
    public Graph getGraph() {
        return graph;
    }
    public double getCurrentTime() {
        return currentTime;
    }
    public void incrementCurrentTime() {
        this.currentTime++;
    }
    public List<Vehicle> getVehicles() {
        return vehicles;
    }
    public List<Request> getRequests() {
        return requests;
    }
    public int getLoadingSpeed() {
        return loadingSpeed;
    }
    public int getRound() {
        return round;
    }
    public boolean[] getFirstGetAnother() {
        return firstGetAnother;
    }
    public void removeRequests(List<Request> requests){
        this.requests.removeAll(requests);
    }
    public void removeRequest(Request request){
        this.requests.remove(request);
    }

    public void addLogEntry(String vehicleName, Location startLocation, double startTime, Location endLocation, double endTime, String boxId, REQUEST_STATUS type){
        String operation = switch (type){
            case SRC -> "PU";
            case SRC_RELOC -> "PL_RELOC";
            case DEST -> "PL";
            case DEST_PU -> "PU";
            case DEST_RELOC -> "PL_RELOC";
            default -> "";
        };
        System.out.println(vehicleName + ";" + startLocation.getX() + ";"+ startLocation.getY() + ";" + (int) startTime  + ";" + endLocation.getX() + ";" + endLocation.getY()   + ";" + (int)endTime + ";"+ boxId + ";" + operation);
        operationLog.add(vehicleName + ";" + startLocation.getX() + ";"+ startLocation.getY() + ";" + (int) startTime  + ";" + endLocation.getX() + ";" + endLocation.getY()   + ";" + (int)endTime + ";"+ boxId + ";" + operation);

    }

    public void writeOperationLog(String out) {
        long time = System.currentTimeMillis() - startingTime;
        StringBuilder output = new StringBuilder();
        for (String logEntry : operationLog) {
            output.append(logEntry+'\n');
            System.out.println(logEntry);
        }
        try(FileWriter fw = new FileWriter(out)){
            fw.write("%vehicle;startx;starty;starttime;endx;endy;endtime;box;operation\n"+output);
        } catch (Exception e){
            System.out.println(e);
        }
        System.out.println("aantal moves: " + operationLog.size());
        System.out.println("Computation time(ms): " + time);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(graph.toString());
        sb.append("\n");
        for (Vehicle vehicle : vehicles) {
            Graph.Pair<GraphNode, Double> pair = this.graph.getClosestNode(vehicle.getLocation());
            sb.append(String.format(vehicle.getName() + " " + vehicle.getLocation().toString() + " is closest to node "
                    + pair.x.getName() + " " + pair.x.getLocation().toString() + " with distance: %.2f (no sqrt)\n", pair.y));
        }
        return sb.toString();
    }

}
