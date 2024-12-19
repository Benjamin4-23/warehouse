package org.kuleuven.engineering.types;

import org.json.JSONArray;
import org.json.JSONObject;

public class Stack implements IStorage {
    private int ID;
    private String name;
    private final int capacity;
    private final java.util.Stack<String> boxes;

    public Stack(JSONObject object, int capacity) {
        ID = object.getInt("ID");
        name = object.getString("name");
        this.capacity = capacity;
        this.boxes = new java.util.Stack<>();
        JSONArray boxArray = object.getJSONArray("boxes");
        for (int i = 0; i < boxArray.length(); i++) {
            String boxName = boxArray.getString(i);
            this.boxes.add(boxName);
        }
    }

    @Override
    public int getID() {
        return ID;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String addBox(String box) {
        return boxes.push(box);
    }

    @Override
    public String removeBox() {
        return boxes.pop();
    }

    @Override
    public String peek() {
        return boxes.peek();
    }

    public boolean isFull() {
        return this.boxes.size() >= this.capacity;
    }

    @Override
    public int getFreeSpace() {
        return capacity - boxes.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBoxesSize() {
        return boxes.size();
    }
    
    public String peakAtDepth(int depth) {
        //return id of box at depth
        if (depth > boxes.size()) {
            return "";
        }
        return boxes.get(boxes.size() - depth - 1);
    }

    public int getDepthOfBox(String boxID) {
        return boxes.search(boxID);
    }

    public Object getBoxes() {
        return boxes.clone();
    }
}
