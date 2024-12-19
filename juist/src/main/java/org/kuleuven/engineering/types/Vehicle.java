package org.kuleuven.engineering.types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.kuleuven.engineering.graph.GraphNode;

public class Vehicle {
    private final int ID;
    private final String name;
    private final int capacity;
    private Location location;
    private int currentRequestID = -1;
    public GraphNode currentNode = null;
    private List<Request> requests;
    private final List<Request> openRequests;
    private final List<Request> simulatedRequests;
    private final List<Request> openSimulatedRequests;
    private final List<Integer> myStackIDs;
    private double unavailableUntil = -1;
    private final ArrayList<String> carriedBoxes;
    private int carriedBoxesCount;

    public Vehicle(JSONObject object) {
        try{
            this.location = new Location(object.getInt("xCoordinate"), object.getInt("yCoordinate"));
            //this.location = new Location(object.getInt("x"), object.getInt("y"));
        } catch (JSONException e){
            this.location = new Location(object.getInt("x"), object.getInt("y"));
        }
        ID = object.getInt("ID");
        name = object.getString("name");
        capacity = object.getInt("capacity");
        this.carriedBoxesCount = 0;
        this.carriedBoxes = new ArrayList<>();
        this.requests = new ArrayList<>();
        this.openRequests = new ArrayList<>();
        this.simulatedRequests = new ArrayList<>();
        this.openSimulatedRequests = new ArrayList<>();
        this.myStackIDs = new ArrayList<>();
    }

    public int getID(){
        return ID;
    }
    public String getName(){
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public int getCurrentRequestID(){
        return currentRequestID;
    }
    
    public void setCurrentRequestID(int id){
        this.currentRequestID = id;
    }

    public int getCarriedBoxesCount(){
        return carriedBoxesCount;
    }
    public int getCapacity(){
        return capacity;
    }

    public GraphNode getCurrentNode(){
        return currentNode;
    }

    public void moveTo(GraphNode node) {
        this.location = node.getLocation();
        this.currentNode = node;
    }

    public boolean isAvailable(double time) {
        return time > unavailableUntil;
    }

    public void setUnavailableUntil(double time){
        this.unavailableUntil = time;
    }
    public double getUnavailableUntil(){
        return unavailableUntil;
    }
    // Getters and setters
    public boolean removeBox(String boxId){
        if (carriedBoxes.contains(boxId)){
            carriedBoxesCount--;
            return carriedBoxes.remove(boxId);
        }
        throw new RuntimeException("Box not found in vehicle at time of removal");
    }
    public void addBox(String boxId){
        this.carriedBoxes.add(boxId);
        this.carriedBoxesCount++;
        if (carriedBoxesCount > capacity){
            throw new RuntimeException("Vehicle capacity exceeded");
        }
    }

    public boolean hasBox(String boxId){
        return carriedBoxes.contains(boxId);
    }
    public String getLastBox(){
        if (carriedBoxes.isEmpty()){
            return null;
        }
        return carriedBoxes.get(carriedBoxes.size() - 1);
    }
    public void resetStackIDs(){
        myStackIDs.clear();
    }

    public void setRequests(List<Request> requests){
        this.requests = requests;
        for (Request request : requests){
            if (request.getPickupLocation().getStorage() instanceof Stack && request.getPlaceLocation().getStorage() instanceof Stack){
                System.out.println("Stack to stack request");
            }
            if (request.getPickupLocation().getStorage() instanceof Stack && !myStackIDs.contains(request.getPickupLocation().getStorage().getID())){
                myStackIDs.add(request.getPickupLocation().getStorage().getID());
            }
            if (request.getPlaceLocation().getStorage() instanceof Stack && !myStackIDs.contains(request.getPlaceLocation().getStorage().getID())){
                myStackIDs.add(request.getPlaceLocation().getStorage().getID());
            }
        }
    }

    public List<Integer> getMyStackIDs(){
        return myStackIDs;
    }

    public List<Request> getRequests(){
        return requests;
    }
    public boolean setNewOpenRequest( HashMap<Integer, Integer> unavailableStacks, double currentTime, int round, boolean doorFirstGetAnother){
        Request currentRequest = null;
        if (doorFirstGetAnother){
            Request firstRequest = requests.get(0);
            if (unavailableStacks.get(firstRequest.getPickupLocation().getStorage().getID()) < currentTime) {
                currentRequest = firstRequest;
            }
        }
        else{
            for (Request request : requests){
                if (round == 0) { // geen relocations mogelijk, dus pak eerste request
                    currentRequest = request;
                    break;
                }
                if (round == 1 && unavailableStacks.get(request.getPickupLocation().getStorage().getID()) < currentTime){
                    currentRequest = request;
                    break;
                }
                if (round == 2 && unavailableStacks.get(request.getPlaceLocation().getStorage().getID()) < currentTime){
                    currentRequest = request;
                    break;
                }
            }
        }
        if (currentRequest != null){
            requests.remove(currentRequest);
            addOpenRequest(currentRequest);
            currentRequestID = currentRequest.getID();
            currentRequest.setAssignedVehicle(ID);
            return true;
        }
        return false;
    }

    public List<Request> getOpenRequests(){
        return openRequests;
    }
    public void addOpenRequest(Request request){
        this.openRequests.add(request);
    }
    public void closeRequest(Request request){
        openRequests.remove(request);
        if (!openRequests.isEmpty()){
            currentRequestID = openRequests.get(0).getID();
        }
        else{
            currentRequestID = -1;
        }
    }
    public void closeRequestLastRound(Request request, int nextRequestID){
        openRequests.remove(request);
        currentRequestID = nextRequestID;
    }
    public void addSimulatedRequest(Request request){
        this.simulatedRequests.add(request);
    }
    public List<Request> getSimulatedRequests(){
        return simulatedRequests;
    }
    public void addOpenSimulatedRequest(Request request){
        this.openSimulatedRequests.add(request);
    }
    public void setNewOpenSimulatedRequest(){
        Request request = simulatedRequests.getLast();
        addOpenSimulatedRequest(request);
        simulatedRequests.remove(request);
        currentRequestID = request.getID();
        request.setAssignedVehicle(ID);
    }
    public List<Request> getOpenSimulatedRequests(){
        return openSimulatedRequests;
    }
    public void closeSimulatedRequest(Request request){
        openSimulatedRequests.remove(request);
        if (!openSimulatedRequests.isEmpty()){
            currentRequestID = openSimulatedRequests.get(0).getID();
        }
        else{
            currentRequestID = -1;
        }
    }
}
