package org.kuleuven.engineering.types;

public interface IStorage {
    int getID();
    String getName();
    String addBox(String box);
    String removeBox();
    String peek();
    boolean isFull();
    int getFreeSpace();
}
