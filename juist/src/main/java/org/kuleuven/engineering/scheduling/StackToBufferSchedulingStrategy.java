package org.kuleuven.engineering.scheduling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.kuleuven.engineering.RequestDistribution;
import org.kuleuven.engineering.RequestHandling;
import org.kuleuven.engineering.Warehouse;
import org.kuleuven.engineering.types.Request;
import org.kuleuven.engineering.types.Stack;
import org.kuleuven.engineering.types.Vehicle;

public class StackToBufferSchedulingStrategy extends SchedulingStrategy {
    private final RequestDistribution requestDistributor;
    private final RequestHandling requestHandler;
    private final Warehouse warehouse;

    public StackToBufferSchedulingStrategy(RequestDistribution requestDistributor, RequestHandling requestHandler, Warehouse warehouse) {
        this.requestDistributor = requestDistributor;
        this.requestHandler = requestHandler;
        this.warehouse = warehouse;
    }

    @Override
    public void schedule() {
        // Get all stack to buffer requests (that weren't handled in top box strategy)
        List<Request> stackToBufferRequests = findStackToBufferRequests();

        // Sort requests based on stack depth to prevent EmptyStackException
        stackToBufferRequests.sort((r1, r2) -> {
            Stack stack1 = (Stack) r1.getPickupLocation().getStorage();
            Stack stack2 = (Stack) r2.getPickupLocation().getStorage();
            return Integer.compare(stack1.getDepthOfBox(r1.getBoxID()), 
                                 stack2.getDepthOfBox(r2.getBoxID()));
        });
        
        // Remove these requests from the main list
        warehouse.removeRequests(stackToBufferRequests);
        
        // Distribute requests over vehicles
        requestDistributor.distributeRequests(stackToBufferRequests,true);
        
        // Execute the scheduling loop
        executeSchedulingLoop();
    }

    @Override
    protected void executeSchedulingLoop() {
        boolean allRequestsDone = false;
        List<Vehicle> vehicles = warehouse.getVehicles();
        HashMap<Integer, Integer> stackIsUsedUntil = warehouse.getStackIsUsedUntil();
        HashMap<Integer, Integer> waitForRequestFinish = warehouse.getWaitForRequestFinish();
        initializeFirstGetAnother();

        while (!allRequestsDone) {
            int round = warehouse.getRound();
            boolean[] firstGetAnother = warehouse.getFirstGetAnother();
            final double currentTime = warehouse.getCurrentTime();

            // remove all relocations that are done
            warehouse.getActiveRelocations().removeIf(x -> x[3] < currentTime);

            for (Vehicle vehicle : vehicles){
                if (vehicle.isAvailable(currentTime)){
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

    private List<Request> findStackToBufferRequests() {
        List<Request> stackToBufferRequests = new ArrayList<>();
        for (Request request : new ArrayList<>(warehouse.getRequests())) {
            if (!request.getPickupLocation().isBuffer() && request.getPlaceLocation().isBuffer()) {
                stackToBufferRequests.add(request);
            }
        }
        return stackToBufferRequests;
    }
    private void initializeFirstGetAnother() {
        for (int i = 0; i < warehouse.getVehicles().size(); i++) {
            warehouse.getFirstGetAnother()[i] = false;
        }
    }
    private void updateFirstGetAnother(Vehicle vehicle){
        List<Request> nextRequests = vehicle.getRequests().stream().toList();
        if (!nextRequests.isEmpty()){
            Request nextRequest = nextRequests.get(0);
            Stack targetStack = (Stack) nextRequest.getPickupLocation().getStorage();
            int neededCapacity = targetStack.getDepthOfBox(nextRequest.getBoxID());
            boolean hasEnoughCapacity = (vehicle.getCapacity() - vehicle.getCarriedBoxesCount()) >= neededCapacity;
            boolean isStackUsed = warehouse.getStackIsUsedUntil().get(nextRequest.getPickupLocation().getStorage().getID()) <= warehouse.getCurrentTime();
            boolean isBoxOnVehicle = vehicle.hasBox(nextRequest.getBoxID());
            boolean isCurrentNodeNotTargetNode = vehicle.getCurrentNode() != nextRequest.getPlaceLocation();
            warehouse.getFirstGetAnother()[warehouse.getVehicles().indexOf(vehicle)] = isStackUsed && hasEnoughCapacity && isBoxOnVehicle && isCurrentNodeNotTargetNode;
        }
        else warehouse.getFirstGetAnother()[warehouse.getVehicles().indexOf(vehicle)] = false;
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