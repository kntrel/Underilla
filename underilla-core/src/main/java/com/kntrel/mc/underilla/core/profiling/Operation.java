package com.kntrel.mc.underilla.core.profiling;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One traced operation and its active stopwatches. */
final class Operation {

    private final UUID id;
    private final Set<Stopwatch> stopwatches = new LinkedHashSet<>();

    Operation(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    UUID id() {
        return id;
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
