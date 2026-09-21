package com.kntrel.mc.underilla.core.profiling;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** A provisioned trace identity that can correlate work across separate scopes. */
public final class Operation {

    private final Instrumenter owner;
    private final UUID id;
    private final Set<Stopwatch> stopwatches = ConcurrentHashMap.newKeySet();

    Operation(Instrumenter owner, UUID id) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.id = Objects.requireNonNull(id, "id");
    }

    public UUID id() {
        return id;
    }

    /**
     * Runs an action inside this operation.
     * Stopwatches started by the owning instrumenter during the action join this operation.
     */
    public void run(Runnable action) {
        owner.run(this, action);
    }

    /**
     * Calls an action inside this operation and returns its result.
     * Stopwatches started by the owning instrumenter during the action join this operation.
     */
    public <T> T call(Supplier<T> action) {
        return owner.call(this, action);
    }

    void requireOwner(Instrumenter instrumenter) {
        if (owner != instrumenter) {
            throw new IllegalArgumentException("operation was provisioned by another instrumenter");
        }
    }

    void add(Stopwatch stopwatch) {
        stopwatches.add(Objects.requireNonNull(stopwatch, "stopwatch"));
    }

    void remove(Stopwatch stopwatch) {
        stopwatches.remove(Objects.requireNonNull(stopwatch, "stopwatch"));
    }

    boolean isEmpty() {
        return stopwatches.isEmpty();
    }
}
