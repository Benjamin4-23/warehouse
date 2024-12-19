package org.kuleuven.engineering.scheduling;

public abstract class SchedulingStrategy {

    public abstract void schedule();
    protected abstract void executeSchedulingLoop();
} 