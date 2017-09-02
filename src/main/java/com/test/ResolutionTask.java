package com.test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by vogel612 on 09.06.17.
 */
public abstract class ResolutionTask extends CompletableFuture<List<Object>> implements Runnable, Comparable<ResolutionTask> {

    protected static final int HIGH_PRIORITY = 10;
    protected static final int MEDIUM_PRIORITY = 5;
    protected static final int LAST_EXECUTE = Integer.MIN_VALUE;

    protected final int priority;

    protected ResolutionTask(int priority) {
        this.priority = priority;
    }

    protected abstract void runImpl() throws Exception;

    @Override
    public final void run() {
        try {
            runImpl();
        } catch (Exception e) {
            completeExceptionally(e);
        }
    }

    @Override
    public int compareTo(ResolutionTask resolutionTask) {
        return priority - resolutionTask.priority;
    }
}
