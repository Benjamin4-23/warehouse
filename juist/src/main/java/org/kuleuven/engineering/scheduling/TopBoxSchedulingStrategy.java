package org.kuleuven.engineering.scheduling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.kuleuven.engineering.RequestDistribution;
import org.kuleuven.engineering.RequestHandling;
import org.kuleuven.engineering.Warehouse;
import org.kuleuven.engineering.types.IStorage;
import org.kuleuven.engineering.types.Request;
import org.kuleuven.engineering.types.Stack;
import org.kuleuven.engineering.types.Vehicle;

public class TopBoxSchedulingStrategy extends SchedulingStrategy {
    private final RequestDistribution requestDistributor;
    private final RequestHandling requestHandler;   
    private final Warehouse warehouse;

    public TopBoxSchedulingStrategy(RequestDistribution requestDistributor, RequestHandling requestHandler, Warehouse warehouse) {
        this.requestDistributor = requestDistributor;
        this.requestHandler = requestHandler;
        this.warehouse = warehouse;
    }

    @Override
    public void schedule() {
        // find requests with topboxes and the boxes on the location below that request that also need to be picked up to go to buffer
        List<Request> requestListWithoutRelocation = findTopBoxRequests();

        // remove requests from main list
        warehouse.removeRequests(requestListWithoutRelocation);

        // sort based on stack depth
        requestListWithoutRelocation = sortRequests(requestListWithoutRelocation);
        
        // distribute over vehicles
        requestDistributor.distributeRequests(requestListWithoutRelocation, true);

        // finish requests 
        executeSchedulingLoop();
    }

    @Override
    protected void executeSchedulingLoop() {
        boolean allRequestsDone = false;
        List<Vehicle> vehicles = warehouse.getVehicles();
        HashMap<Integer, Integer> stackIsUsedUntil = warehouse.getStackIsUsedUntil();
        HashMap<Integer, Integer> waitForRequestFinish = warehouse.getWaitForRequestFinish();
        // keep track of whether the vehicle has to get another box first (initialize)
        initializeFirstGetAnother();

        while (!allRequestsDone) {
            int round = warehouse.getRound();
            boolean[] firstGetAnother = warehouse.getFirstGetAnother();
            final double currentTime = warehouse.getCurrentTime();

            // remove all relocations that are done
            warehouse.getActiveRelocations().removeIf(x -> x[3] < currentTime);

            for (Vehicle vehicle : warehouse.getVehicles()){
                if (vehicle.isAvailable(warehouse.getCurrentTime())){
                    boolean hasSpace = vehicle.getCapacity() > vehicle.getCarriedBoxesCount();
                    boolean notWorkingOnRequest = vehicle.getCurrentRequestID() == -1;
                    boolean hasRequestAvailable = !vehicle.getRequests().isEmpty();
                    boolean getAnotherFirst = firstGetAnother[vehicles.indexOf(vehicle)];
                    boolean hasOpenRequests = !vehicle.getOpenRequests().isEmpty();

                    if (!getAnotherFirst && hasSpace && notWorkingOnRequest && hasRequestAvailable && !hasOpenRequests) {
                        boolean success = vehicle.setNewOpenRequest(stackIsUsedUntil, currentTime, round, false);
                        if (!success) continue; // can't open request because the stack it needs to go to is still used? wait
                        Request request = vehicle.getOpenRequests().stream().filter(x -> {return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0);
                        requestHandler.handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        updateFirstGetAnother(vehicle);
                    }

                    else if (getAnotherFirst){
                        boolean success = vehicle.setNewOpenRequest(stackIsUsedUntil, currentTime, round, true);
                        if (!success) continue;
                        Request request = vehicle.getOpenRequests().stream().filter(x -> {return x.getID() == vehicle.getCurrentRequestID();}).toList().get(0);
                        requestHandler.handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        updateFirstGetAnother(vehicle);
                    }

                    else if (!notWorkingOnRequest && !getAnotherFirst && !vehicle.getOpenRequests().isEmpty()){
                        // go to buffer and finish open requests one by one
                        Request request = vehicle.getOpenRequests().stream().filter(x -> {return x.getAssignedVehicle() == vehicle.getID();}).toList().get(0);
                        requestHandler.handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        // updateFirstGetAnother(vehicle, currentTime, round);

                        // handle finished requests
                        if (request.isDone()){
                            vehicle.closeRequest(request);
                            // if there is another vehicle waiting for the completion of this request
                            if (waitForRequestFinish.containsKey(request.getID())){
                                int vehicleID = waitForRequestFinish.get(request.getID());
                                Vehicle vehicleWaiting = vehicles.stream().filter(v -> v.getID() == vehicleID).toList().get(0);
                                vehicleWaiting.setUnavailableUntil(vehicle.getUnavailableUntil());
                                waitForRequestFinish.remove(request.getID());
                            }
                        }
                    }
                }
            }
            
            // loop over vehicles and check if they all have an empty requestlist
            allRequestsDone = checkIfAllRequestsDone();

            if (!allRequestsDone) warehouse.incrementCurrentTime();
        }
    }

    private List<Request> findTopBoxRequests() {
        List<Request> requestListWithoutRelocation = new ArrayList<>();
        List<Request> tempList = new ArrayList<>(warehouse.getRequests()); 
        
        for (Request request : tempList) {
            IStorage storage = request.getPickupLocation().getStorage();
            if (!request.getPickupLocation().isBuffer() && 
                storage.peek().equals(request.getBoxID()) && 
                request.getPlaceLocation().isBuffer()) {
                
                requestListWithoutRelocation.add(request);
                warehouse.removeRequest(request);
                
                // find if the box below also needs to be picked up
                if (storage instanceof Stack stack) {
                    for (int i = 1; i < stack.getBoxesSize(); i++) {
                        String boxIDBelow = stack.peakAtDepth(i);
                        // if that box also needs to be picked up, add that request
                        if (!findAndAddRequest(tempList, boxIDBelow, requestListWithoutRelocation)) {
                            break;
                        }
                    }
                }
            }
        }
        return requestListWithoutRelocation;
    }
    private boolean findAndAddRequest(List<Request> tempList, String boxID, List<Request> requestList) {
        for (Request request : tempList) {
            if (request.getBoxID().equals(boxID)) {
                requestList.add(request);
                warehouse.removeRequest(request);
                return true;
            }
        }
        return false;
    }
    private List<Request> sortRequests(List<Request> requests) {
        requests.sort((r1, r2) -> {
            if (r1.getPickupLocation().getStorage() instanceof Stack stack1 && 
                r2.getPickupLocation().getStorage() instanceof Stack stack2) {
                return Integer.compare(stack1.getDepthOfBox(r1.getBoxID()), 
                                    stack2.getDepthOfBox(r2.getBoxID()));
            }
            return 0;
        });
        return requests;
    }
    private void initializeFirstGetAnother() {
        for (int i = 0; i < warehouse.getVehicles().size(); i++) {
            warehouse.getFirstGetAnother()[i] = false;
        }
    }
    private void updateFirstGetAnother(Vehicle vehicle){
        warehouse.getFirstGetAnother()[warehouse.getVehicles().indexOf(vehicle)] = vehicle.getCapacity() > vehicle.getCarriedBoxesCount() && !vehicle.getRequests().isEmpty();
    }
    private boolean checkIfAllRequestsDone(){
        boolean allRequestsDone = true;
        for (Vehicle vehicle : warehouse.getVehicles()){
            if (!vehicle.getRequests().isEmpty() || !vehicle.getOpenRequests().isEmpty()){
                allRequestsDone = false;
                break;
            }
        }
        return allRequestsDone;
    }
    
    
} 