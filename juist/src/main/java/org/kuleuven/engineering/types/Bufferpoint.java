package org.kuleuven.engineering.types;

import java.util.Stack;

import org.json.JSONObject;

public class Bufferpoint implements IStorage {
    private int ID;
    private String name;
    private Stack<String> boxes = new Stack<>();

    public Bufferpoint(JSONObject object) {
        ID = object.getInt("ID");
        name = object.getString("name");
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
        return box;
    }

    @Override
    public String removeBox() {
        return boxes.pop();
    }

    @Override
    public String peek() {
        return boxes.peek();
    }

    @Override
    public boolean isFull() {
        return false;
    }

    @Override
    public int getFreeSpace() { // niet nodig?
        return 100;
    }
}
