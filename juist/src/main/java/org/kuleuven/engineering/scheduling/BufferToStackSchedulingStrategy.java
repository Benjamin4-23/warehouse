package org.kuleuven.engineering.scheduling;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.kuleuven.engineering.RequestDistribution;
import org.kuleuven.engineering.RequestHandling;
import org.kuleuven.engineering.Warehouse;
import org.kuleuven.engineering.graph.GraphNode;
import org.kuleuven.engineering.types.Location;
import org.kuleuven.engineering.types.REQUEST_STATUS;
import org.kuleuven.engineering.types.Request;
import org.kuleuven.engineering.types.Stack;
import org.kuleuven.engineering.types.Vehicle;

public class BufferToStackSchedulingStrategy extends SchedulingStrategy {
    private final RequestDistribution requestDistributor;
    private final RequestHandling requestHandler;
    private final Warehouse warehouse;


    public BufferToStackSchedulingStrategy(RequestDistribution requestDistributor, RequestHandling requestHandler, Warehouse warehouse) {
        this.requestDistributor = requestDistributor;
        this.requestHandler = requestHandler;
        this.warehouse = warehouse;
    }

    @Override
    public void schedule() {
        // Get all buffer to stack requests
        List<Request> bufferToStackRequests = findBufferToStackRequests();

        // These should be all remaining requests, but clear the main list anyway
        warehouse.removeRequests(bufferToStackRequests);

        // check if enough space for requests
        if (!checkIfEnoughSpace(bufferToStackRequests)) return;
        
        // Distribute requests over vehicles
        requestDistributor.distributeRequests(bufferToStackRequests, false);
        
        // Execute the scheduling loop
        executeSchedulingLoop();
    }

    @Override
    protected void executeSchedulingLoop() {
        boolean allRequestsDone = false;
        List<Vehicle> vehicles = warehouse.getVehicles();

        while (!allRequestsDone){
            final double currentTime = warehouse.getCurrentTime();
            warehouse.getActiveRelocations().removeIf(x -> x[3] < currentTime);

            for (Vehicle vehicle : vehicles){
                if (vehicle.isAvailable(currentTime) && (!vehicle.getRequests().isEmpty() || !vehicle.getOpenRequests().isEmpty())){
                    if (!vehicle.getRequests().isEmpty() && vehicle.getOpenRequests().isEmpty()){
                        // open requests with same destination
                        Location dest = vehicle.getRequests().get(0).getPlaceLocation().getLocation();
                        List<Request> requestsWithSameDest = vehicle.getRequests().stream().filter(x -> {return x.getPlaceLocation().getLocation() == dest;}).toList();
                        for (Request request : requestsWithSameDest){
                            vehicle.addOpenRequest(request);
                            vehicle.getRequests().remove(request);
                        }
                    }

                    // calculate how much space is still needed on dest stack
                    int neededCapacity = vehicle.getOpenRequests().size();
                    Stack stack  = (Stack) vehicle.getOpenRequests().get(0).getPlaceLocation().getStorage();
                    int freeSpace = stack.getFreeSpace();
                    int requiredExtraCapacity = neededCapacity - freeSpace;

                    if (vehicle.getCurrentRequestID() == -1 && requiredExtraCapacity > 0){
                        // make simulated request to move topbox to tempstack to make space on dest stack
                        makeSimulatedRequest(vehicle, stack);
                        Request request = vehicle.getOpenSimulatedRequests().get(0);
                        requestHandler.handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                    }

                    else if (vehicle.getCurrentRequestID() != -1 && requiredExtraCapacity >= 0 && !vehicle.getOpenSimulatedRequests().isEmpty()){
                        // finish simulated requests
                        Request request = vehicle.getOpenSimulatedRequests().get(0);
                        requestHandler.handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (request.isDone()) vehicle.closeSimulatedRequest(request);
                    }

                    else if (vehicle.getCurrentRequestID() == -1 && requiredExtraCapacity <= 0 && !vehicle.getOpenRequests().isEmpty()) {
                        // begin with open requests
                        Request request = vehicle.getOpenRequests().get(0);
                        vehicle.setCurrentRequestID(request.getID());
                        requestHandler.handleRequest(vehicle, request, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (request.isDone()) vehicle.closeRequest(request);
                    }

                    else if (vehicle.getCurrentRequestID() != -1 && requiredExtraCapacity <= 0 && !vehicle.getOpenRequests().isEmpty()){
                        // if box of first request is picked up, check if another box can be picked up
                        Request currentRequest = vehicle.getOpenRequests().stream().filter(x -> x.getID() == vehicle.getCurrentRequestID()).toList().get(0);
                        Request nextRequest = findNextRequest(vehicle, currentRequest);

                        if (nextRequest != null){
                            vehicle.setCurrentRequestID(nextRequest.getID());
                            currentRequest = vehicle.getOpenRequests().stream().filter(x -> x.getID() == vehicle.getCurrentRequestID()).toList().get(0);
                        }

                        // handle current request
                        requestHandler.handleRequest(vehicle, currentRequest, currentTime > 0 ? currentTime-1 : 0, 0);
                        if (currentRequest.isDone()){
                            // handle finished request
                            List<Request> openRequests = vehicle.getOpenRequests().stream().filter(x -> vehicle.hasBox(x.getBoxID())).toList();
                            if (!openRequests.isEmpty()) vehicle.closeRequestLastRound(currentRequest, openRequests.get(0).getID());
                            else vehicle.closeRequest(currentRequest);
                        }
                    }

                }
            }

            
            allRequestsDone = checkIfAllRequestsDone();
            if (!allRequestsDone) warehouse.incrementCurrentTime();
        }
    }

    private List<Request> findBufferToStackRequests() {
        List<Request> bufferToStackRequests = new ArrayList<>();
        for (Request request : new ArrayList<>(warehouse.getRequests())) {
            if (request.getPickupLocation().isBuffer() && !request.getPlaceLocation().isBuffer()) {
                bufferToStackRequests.add(request);
            }
        }
        return bufferToStackRequests;
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
    private boolean checkIfEnoughSpace(List<Request> requestsCopy){
        // check of genoeg plaats voor alle requests
        int totalFreeSpace = 0;
        for (GraphNode node : warehouse.getGraph().getNodes()){
            if (node.getStorage() instanceof Stack stack){
                totalFreeSpace += stack.getFreeSpace();
            }
        }
        if (totalFreeSpace < requestsCopy.size()){
            System.out.println("not enough space for requests");
            return false;
        }
        return true;
    }
    private void makeSimulatedRequest(Vehicle vehicle, Stack stack){
        GraphNode src = vehicle.getOpenRequests().get(0).getPickupLocation();
        GraphNode dest = vehicle.getOpenRequests().get(0).getPlaceLocation();
        List<GraphNode> tempstacks = warehouse.findNStorage(1, src, dest, REQUEST_STATUS.SIMULATED, vehicle);
        if (tempstacks.isEmpty()){
            return;
        }
        GraphNode tempStack = tempstacks.get(0);
        int newID = Integer.MAX_VALUE-(vehicle.getSimulatedRequests().size()+vehicle.getOpenRequests().size());
        Request simulatedRequest = new Request(dest, tempStack, newID, stack.peek());
        vehicle.addSimulatedRequest(simulatedRequest);
        vehicle.setNewOpenSimulatedRequest();
    }
    private Request findNextRequest(Vehicle vehicle, Request currentRequest){
        boolean hasBoxOnVehicle = currentRequest.getBoxID().equals(vehicle.getLastBox());
        boolean hasEnoughCapacity = vehicle.getCapacity() > vehicle.getCarriedBoxesCount();
        boolean isCurrentNodeSameAsPickupLocation = vehicle.getCurrentNode() == currentRequest.getPickupLocation();

        if (vehicle.getOpenRequests().size() > 1 && hasBoxOnVehicle && isCurrentNodeSameAsPickupLocation && hasEnoughCapacity){
            Predicate<Request> checkfunction = x -> x.getPickupLocation().equals(currentRequest.getPickupLocation()) && !vehicle.hasBox(x.getBoxID());
            List<Request> openRequestsSameSrc = vehicle.getOpenRequests().stream().filter(checkfunction).toList();
            if (!openRequestsSameSrc.isEmpty()) return openRequestsSameSrc.get(0);
            else {
                Predicate<Request> checkfunction2 = x -> x.getPickupLocation().isBuffer() && !vehicle.hasBox(x.getBoxID());
                List<Request> openRequestsDifferentSrc = vehicle.getOpenRequests().stream().filter(checkfunction2).toList();
                if (!openRequestsDifferentSrc.isEmpty()) return openRequestsDifferentSrc.get(0);
            }
        }
        return null;
    }

} 