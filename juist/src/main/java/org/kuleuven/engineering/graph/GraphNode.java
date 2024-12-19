package org.kuleuven.engineering.graph;

import org.kuleuven.engineering.types.Bufferpoint;
import org.kuleuven.engineering.types.IStorage;
import org.kuleuven.engineering.types.Location;

public class GraphNode {
    private final boolean isBuffer;
    private final IStorage storage;
    private final Location location;

    public GraphNode(IStorage storage, Location location){
        this.location = location;
        this.storage = storage;
        this.isBuffer = storage instanceof Bufferpoint;
    }
    public String getName() {
        return storage.getName();
    }
    public boolean isBuffer() {
        return isBuffer;
    }
    public Location getLocation() {
        return location;
    }
    public IStorage getStorage() {
        return storage;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GraphNode graphNode){
            return graphNode.getLocation().equals(this.getLocation());
        }
        return false;
    }


}
