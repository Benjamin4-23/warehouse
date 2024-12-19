package org.kuleuven.engineering;

import java.util.HashMap;
import java.util.List;

import org.kuleuven.engineering.graph.Graph;
import org.kuleuven.engineering.graph.GraphNode;
import org.kuleuven.engineering.types.Location;
import org.kuleuven.engineering.types.REQUEST_STATUS;
import org.kuleuven.engineering.types.Request;
import org.kuleuven.engineering.types.Stack;
import org.kuleuven.engineering.types.Vehicle;

public class RequestHandling {
    private boolean targetStackIsUsed;
    private boolean noAvailableTempStack;
    private final int loadingSpeed;
    private final Warehouse warehouse;

    public RequestHandling(int loadingSpeed, Warehouse warehouse) {
        this.loadingSpeed = loadingSpeed;
        this.warehouse = warehouse;
        targetStackIsUsed = false;
        noAvailableTempStack = false;
    }

    // handle request based on status
    public boolean handleRequest(Vehicle vehicle, Request request, double time, int sameDestStackCount){
        Location startLocation = vehicle.getLocation();
        double timeAfterMove = time;

        return switch (request.getStatus()) {
            case INITIAL -> handleIinitialStatus(vehicle, request, time, startLocation, timeAfterMove);
            case SRC -> handleSrcStatus(vehicle, request, time, startLocation, timeAfterMove);
            case DEST_PU -> handleDestPUStatus(vehicle, request, time, sameDestStackCount, startLocation, timeAfterMove);
            default -> false;
        };
    }
    
    private boolean checkAndResetTargetStackUsed(boolean condition) {
        if (condition) {
            targetStackIsUsed = false;
            return false;
        }
        return true;
    }
    private boolean resetNoAvailableTempStack(boolean condition){
        if (condition){
            noAvailableTempStack = false;
            return false;
        }
        return true;
    }
    private boolean handleIinitialStatus(Vehicle vehicle, Request request, double time, Location startLocation, double timeAfterMove){
        // if vehicle is not empty and at a stack, try to empty vehicle as much as possible
        // check if vehicle got a needed box and is collecting more boxes
        boolean result = leegVehicle(vehicle, startLocation, timeAfterMove, time, request, warehouse.getStackIsUsedUntil(), warehouse.getWaitForRequestFinish());
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        // if dest is a stack and full, then relocate or check if multiple requests also go to the same stack, so much space is freed on dest stack, 
        // then all those requests are finished
        if (!result) {
            result = maakPlaatsVrijOpDest(vehicle, startLocation, timeAfterMove, time, request, warehouse.getStackIsUsedUntil(), warehouse.getGraph());
            if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        }
        // go to src and PU
        if (!result) {
            PickupSrc(vehicle, startLocation, timeAfterMove, time, request, warehouse.getStackIsUsedUntil(), warehouse.getGraph());
            if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        } 
        return true;
    }
    private boolean handleSrcStatus(Vehicle vehicle, Request request, double time, Location startLocation, double timeAfterMove){
        // check if relocation is needed (we don't have the box on vehicle), if vehicle is full and not the correct box, go to temp stack
        boolean result = boxesRelocatenNaarTempStack(vehicle, startLocation, timeAfterMove, timeAfterMove, request, warehouse.getStackIsUsedUntil(), warehouse.getWaitForRequestFinish(), warehouse.getGraph(), warehouse.getActiveRelocations());
        if (!resetNoAvailableTempStack(noAvailableTempStack)) return false;
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        
        // if vehicle is not full and not the correct box, try to take 1 more
        if (!result) result = NeemNogEenboxOpBijSrc(vehicle, startLocation, timeAfterMove, time, request, warehouse.getStackIsUsedUntil(), warehouse.getGraph(), warehouse.getActiveRelocations());
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;

        // go to dest and PL
        if (!result) placeBoxBijDest(vehicle, startLocation, timeAfterMove, time, request, warehouse.getStackIsUsedUntil(), warehouse.getGraph());
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        return true;
    }
    private boolean handleDestPUStatus(Vehicle vehicle, Request request, double time, int sameDestStackCount, Location startLocation, double timeAfterMove){
        // if vehicle is not full and sameDestStackCount > 0 and stack.freeSpace < sameDestStackCount+1, try to take 1 more
        boolean result = neemNogBoxOpDest(vehicle, startLocation, timeAfterMove, time, request, sameDestStackCount, warehouse.getStackIsUsedUntil(), warehouse.getGraph());
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;

        // go to temp stack
        if (!result) placeAtTempStackDest(vehicle, startLocation, timeAfterMove, time, request, warehouse.getStackIsUsedUntil(), warehouse.getWaitForRequestFinish(), warehouse.getGraph(), warehouse.getActiveRelocations());
        if (!resetNoAvailableTempStack(noAvailableTempStack)) return false;
        if (!checkAndResetTargetStackUsed(targetStackIsUsed)) return false;
        return true;
    }




    

    // helper functions
    private boolean leegVehicle(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, HashMap<Integer, Integer> stackIsUsedUntil, HashMap<Integer, Integer> waitForRequestFinish) {
        boolean vehicleGotRequestBox = hasBoxInOpenRequests(vehicle);
        boolean canUnloadUnwantedBox = !vehicleGotRequestBox && vehicle.getCarriedBoxesCount() > 0;
        boolean notAtPickupLocation = vehicle.getCurrentNode() != request.getPickupLocation();

        if (canUnloadUnwantedBox && notAtPickupLocation && vehicle.getCurrentNode().getStorage() instanceof Stack stack && stack.getFreeSpace() > 0){
            String box = vehicle.getLastBox();
            double timeAfterOperation = timeAfterMove + loadingSpeed;
            
            if (isStackAvailable(stack, time, stackIsUsedUntil)) warehouse.getStackIsUsedUntil().put(stack.getID(), (int) timeAfterOperation);
            else return false;

            vehicle.setUnavailableUntil(timeAfterOperation);
            stack.addBox(box);
            vehicle.removeBox(box);
            warehouse.addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.DEST_RELOC);
            return true;
        }
        return false;
    }
    private boolean maakPlaatsVrijOpDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, HashMap<Integer, Integer> stackIsUsedUntil, Graph graph) {
        GraphNode dest = request.getPlaceLocation();
        if (dest.getStorage() instanceof Stack stack && stack.getFreeSpace() < 1){
            // maak plaats vrij op dest stack 
            if (startLocation != dest.getLocation()) timeAfterMove += graph.getTravelTime(vehicle, dest);
            double timeAfterOperation = timeAfterMove + loadingSpeed;

            if (isStackAvailable(stack, time, stackIsUsedUntil)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            else return false;

            vehicle.setUnavailableUntil(timeAfterOperation);
            vehicle.moveTo(dest);
            String box = stack.removeBox();
            vehicle.addBox(box);
            warehouse.addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.DEST_PU);
            request.setStatus(REQUEST_STATUS.DEST_PU);
            return true;
        }
        return false;
    }
    private void PickupSrc(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, HashMap<Integer, Integer> stackIsUsedUntil, Graph graph) {
        GraphNode src = request.getPickupLocation();
        if (startLocation != src.getLocation()) timeAfterMove += graph.getTravelTime(vehicle, src);
        double timeAfterOperation = timeAfterMove + loadingSpeed;

        if (src.getStorage() instanceof Stack stack){
            if (isStackAvailable(stack, time, stackIsUsedUntil)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            else return;
        }

        vehicle.setUnavailableUntil(timeAfterOperation);
        vehicle.moveTo(src);

        String box = "";
        if (src.getStorage() instanceof Stack stack){
            box = stack.removeBox();
            vehicle.addBox(box);
        }
        else vehicle.addBox(request.getBoxID()); 

        warehouse.addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.SRC);
        request.setStatus(REQUEST_STATUS.SRC);
    }
    
    private boolean boxesRelocatenNaarTempStack(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, HashMap<Integer, Integer> stackIsUsedUntil, HashMap<Integer, Integer> waitForRequestFinish, Graph graph, List<Integer[]> activeRelocations) {
        if (!vehicle.hasBox(request.getBoxID()) && vehicle.getCapacity() == vehicle.getCarriedBoxesCount() && vehicle.getCarriedBoxesCount() > 0){
            String box = vehicle.getLastBox();
            GraphNode src = request.getPickupLocation();
            GraphNode dest = request.getPlaceLocation();
            REQUEST_STATUS status = request.getStatus();
            List<GraphNode> tempStacks = warehouse.findNStorage(1, src, dest, status, vehicle);
            if (tempStacks.isEmpty()){
                noAvailableTempStack = true;
                return false;
            }
            GraphNode tempStack = tempStacks.get(0);
            Stack stack = (Stack) tempStack.getStorage();
            
            timeAfterMove += graph.getTravelTime(vehicle, tempStack);
            double timeAfterOperation = timeAfterMove + loadingSpeed;

            // als er al een relocation bezig is van die stack naar hier, wacht tot de andere klaar is met dat request (anders werken ze elkaar tegen)
            for (Integer[] relocation : activeRelocations){
                if (relocation[1] == ((Stack) vehicle.getCurrentNode().getStorage()).getID() && relocation[0] == ((Stack) tempStack.getStorage()).getID()){
                    waitForRequestFinish.put(relocation[2], vehicle.getID());
                    vehicle.setUnavailableUntil(Double.MAX_VALUE);
                    noAvailableTempStack = true;
                    return false;
                }
            }

            if (isStackAvailable(stack, time, stackIsUsedUntil)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            else return false;

            vehicle.setUnavailableUntil(timeAfterOperation);
            int prevVehicleLocation = ((Stack) vehicle.getCurrentNode().getStorage()).getID();
            vehicle.moveTo(tempStack);
            stack.addBox(box);
            vehicle.removeBox(box);
            warehouse.addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.SRC_RELOC);
            request.setStatus(REQUEST_STATUS.INITIAL);
            stackIsUsedUntil.put(((Stack) tempStack.getStorage()).getID(), (int) (timeAfterOperation));
            activeRelocations.add(new Integer[]{prevVehicleLocation, ((Stack) tempStack.getStorage()).getID(), request.getID(), (int) timeAfterOperation});
            return true;
        }
        return false;
    }
    private boolean NeemNogEenboxOpBijSrc(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, HashMap<Integer, Integer> stackIsUsedUntil, Graph graph, List<Integer[]> activeRelocations) {
        if (!vehicle.hasBox(request.getBoxID()) && vehicle.getCapacity() > vehicle.getCarriedBoxesCount()){
            double timeAfterOperation = timeAfterMove + loadingSpeed;
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                if (isStackAvailable(stack, time, stackIsUsedUntil)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
                else return false;
            }
            vehicle.setUnavailableUntil(timeAfterOperation);
            String box = "";
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                box = stack.removeBox();
                vehicle.addBox(box);
            }
            else vehicle.addBox(request.getBoxID());
            warehouse.addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.SRC);
            return true;
        }
        return false;
    }
    private void placeBoxBijDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, HashMap<Integer, Integer> stackIsUsedUntil, Graph graph) {
        GraphNode dest = request.getPlaceLocation();
        if (dest.getStorage() instanceof Stack stack && stack.getFreeSpace() == 0){
            return;
        }
        if (startLocation != dest.getLocation()) timeAfterMove += graph.getTravelTime(vehicle, dest);
        double timeAfterOperation = timeAfterMove + loadingSpeed;

        if (dest.getStorage() instanceof Stack stack){
            if (isStackAvailable(stack, time, stackIsUsedUntil)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
            else return;
        }
        
        vehicle.setUnavailableUntil(timeAfterOperation);
        vehicle.moveTo(dest);
        if (dest.getStorage() instanceof Stack stack){
            stack.addBox(request.getBoxID());
        }
        vehicle.removeBox(request.getBoxID());
        warehouse.addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, request.getBoxID(), REQUEST_STATUS.DEST);
        request.setStatus(REQUEST_STATUS.DEST);
    }

    private boolean neemNogBoxOpDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, int sameDestStackCount, HashMap<Integer, Integer> stackIsUsedUntil, Graph graph) {
        if (vehicle.getCapacity() > vehicle.getCarriedBoxesCount() && sameDestStackCount > 0 && ((Stack)request.getPlaceLocation().getStorage()).getFreeSpace() < sameDestStackCount+1){
            double timeAfterOperation = timeAfterMove + loadingSpeed;
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                if (isStackAvailable(stack, time, stackIsUsedUntil)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
                else return false;
            }
            vehicle.setUnavailableUntil(timeAfterOperation);
            String box = "";
            if (vehicle.getCurrentNode().getStorage() instanceof Stack stack){
                box = stack.removeBox();
                vehicle.addBox(box);
            }
            else vehicle.addBox(request.getBoxID());
            warehouse.addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, vehicle.getLastBox(), REQUEST_STATUS.DEST_PU);
            return true;
        }
        return false;
    }
    private void placeAtTempStackDest(Vehicle vehicle, Location startLocation, double timeAfterMove, double time, Request request, HashMap<Integer, Integer> stackIsUsedUntil, HashMap<Integer, Integer> waitForRequestFinish, Graph graph, List<Integer[]> activeRelocations) {
        String box = vehicle.getLastBox();
        GraphNode src = request.getPickupLocation();
        GraphNode dest = request.getPlaceLocation();
        REQUEST_STATUS status = request.getStatus();
        List<GraphNode> tempStacks = warehouse.findNStorage(1, src, dest, status, vehicle);
        if (tempStacks.isEmpty()){
            noAvailableTempStack = true;
            return;
        }
        GraphNode tempStack = tempStacks.get(0);
        Stack stack = (Stack) tempStack.getStorage();
        timeAfterMove += graph.getTravelTime(vehicle, tempStack);
        double timeAfterOperation = timeAfterMove + loadingSpeed;

        for (Integer[] relocation : activeRelocations){
            if (relocation[1] == ((Stack) vehicle.getCurrentNode().getStorage()).getID() && relocation[0] == ((Stack) tempStack.getStorage()).getID()){
                waitForRequestFinish.put(relocation[2], vehicle.getID());
                vehicle.setUnavailableUntil(Double.MAX_VALUE);
                noAvailableTempStack = true;
                return;
            }
        }

        if (isStackAvailable(stack, time, stackIsUsedUntil)) stackIsUsedUntil.put(stack.getID(), (int) timeAfterOperation);
        else return;
        vehicle.setUnavailableUntil(timeAfterOperation);
        
        int prevVehicleLocation = ((Stack) vehicle.getCurrentNode().getStorage()).getID();
        vehicle.moveTo(tempStack);
        stack.addBox(box);
        vehicle.removeBox(box);
        warehouse.addLogEntry(vehicle.getName(), startLocation, time, vehicle.getLocation(), timeAfterOperation, box, REQUEST_STATUS.DEST_RELOC);
        request.setStatus(REQUEST_STATUS.INITIAL);
        stackIsUsedUntil.put(((Stack) tempStack.getStorage()).getID(), (int) (timeAfterOperation));
        activeRelocations.add(new Integer[]{prevVehicleLocation, ((Stack) tempStack.getStorage()).getID(), request.getID(), (int) timeAfterOperation});
    }

    private boolean isStackAvailable(Stack stack, double time, HashMap<Integer, Integer> stackIsUsedUntil) {
        if (stackIsUsedUntil.get(stack.getID()) <= time) return true;
        targetStackIsUsed = true;
        return false;
    }
    private boolean hasBoxInOpenRequests(Vehicle vehicle) {
        for (Request req : vehicle.getOpenRequests()) {
            if (vehicle.hasBox(req.getBoxID())) {
                return true;
            }
        }
        return false;
    }
}
