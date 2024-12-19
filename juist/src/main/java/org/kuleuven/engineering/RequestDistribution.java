package org.kuleuven.engineering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.kuleuven.engineering.types.Request;
import org.kuleuven.engineering.types.Stack;
import org.kuleuven.engineering.types.Vehicle;


public class RequestDistribution {
    private final List<Vehicle> vehicles;
    private final List<List<Request>> requestsPerVehicleList;

    public RequestDistribution(List<Vehicle> vehicles, List<List<Request>> requestsPerVehicleList) {
        this.vehicles = vehicles;
        this.requestsPerVehicleList = requestsPerVehicleList;
    }

    // distribute requests over vehicles based on stack load    
    public void distributeRequests(List<Request> requestList, boolean usePickupLocation) {
        HashMap<Integer, Integer> stackLoad = calculateStackLoad(requestList, usePickupLocation);
        int requestsPerVehicle = (requestList.size() / vehicles.size()) + 1;
        initializeRequestsPerVehicleList();

        // sort stackIDs based on stackLoad size
        List<Integer> stackIDs = new ArrayList<>(stackLoad.keySet());
        stackIDs.sort((s1, s2) -> stackLoad.get(s2).compareTo(stackLoad.get(s1)));

        // create lists of requests for each vehicle
        createVehicleRequestLists(requestList, usePickupLocation, requestsPerVehicle, stackIDs);

        // give each vehicle its requests
        for (int i = 0; i < vehicles.size(); i++) {
            vehicles.get(i).setRequests(requestsPerVehicleList.get(i));
        }
    }

    // returns a map with the stack id as key and the number of requests for that stack as value
    private HashMap<Integer, Integer> calculateStackLoad(List<Request> requestList, boolean usePickupLocation) {
        HashMap<Integer, Integer> stackLoad = new HashMap<>();
        for (Request request : requestList) {
            Stack stack = (Stack) (usePickupLocation ?
                request.getPickupLocation().getStorage() :
                request.getPlaceLocation().getStorage());

            int stackId = stack.getID();
            stackLoad.merge(stackId, 1, Integer::sum);
        }
        return stackLoad;
    }

    private void initializeRequestsPerVehicleList(){
        for (Vehicle vehicle : vehicles) {
            requestsPerVehicleList.add(new ArrayList<>());
        }
    }
    private void createVehicleRequestLists(List<Request> requestList, boolean usePickupLocation, int requestsPerVehicle, List<Integer> stackIDs){
        int vehicleIndex = 0;
        for (Integer stackID : stackIDs) {
            // if a vehicle has enough requests, go to the next vehicle
            while (requestsPerVehicleList.get(vehicleIndex).size() >= requestsPerVehicle) {
                vehicleIndex++;
            }

            // Filter requests based on location type
            List<Request> requestsForStack = requestList.stream()
                .filter(x -> {
                    Stack stack = (Stack) (usePickupLocation ?
                        x.getPickupLocation().getStorage() :
                        x.getPlaceLocation().getStorage());
                    return stack.getID() == stackID;
                })
                .toList();

            requestsPerVehicleList.get(vehicleIndex).addAll(requestsForStack);
            vehicleIndex++;
            if (vehicleIndex == vehicles.size()) {
                vehicleIndex = 0;
            }
        }
    }
}
