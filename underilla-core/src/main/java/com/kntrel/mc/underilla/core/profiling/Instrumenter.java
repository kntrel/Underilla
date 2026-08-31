package com.kntrel.mc.underilla.core.profiling;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Creates subject-scoped trackers and routes their measurements to a recorder. */
public final class Instrumenter {

    private final Recorder recorder;
    private final LongSupplier nanoTime;
    private final Clock clock;
    private final Supplier<UUID> operationIds;
    private final ThreadLocal<Operation> currentOperation = new ThreadLocal<>();

    public Instrumenter(Recorder recorder) {
        this(recorder, System::nanoTime, Clock.systemUTC(), UUID::randomUUID);
    }

    Instrumenter(Recorder recorder, LongSupplier nanoTime) {
        this(recorder, nanoTime, Clock.systemUTC(), UUID::randomUUID);
    }

    Instrumenter(Recorder recorder, LongSupplier nanoTime, Clock clock) {
        this(recorder, nanoTime, clock, UUID::randomUUID);
    }

    Instrumenter(Recorder recorder, LongSupplier nanoTime, Clock clock, Supplier<UUID> operationIds) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
    }

    /** Creates a tracker scoped to the supplied subject class. */
    public Tracker tracker(Class<?> subject) {
        return new Tracker(this, Objects.requireNonNull(subject, "subject"));
    }

    Stopwatch stopwatch(Class<?> subject, String event) {
        Operation operation = currentOperation.get();
        if (operation == null) {
            operation = new Operation(operationIds.get());
            currentOperation.set(operation);
        }

        Stopwatch stopwatch = new Stopwatch(this, operation, subject, event, nanoTime.getAsLong());
        operation.add(stopwatch);
        return stopwatch;
    }

    void stop(Stopwatch stopwatch) {
        Operation operation = stopwatch.operation();
        long stoppedAtNanos = nanoTime.getAsLong();
        Instant capturedAt = Instant.now(clock);
        operation.remove(stopwatch);
        stopwatch.markStopped();

        if (operation.isEmpty() && currentOperation.get() == operation) {
            currentOperation.remove();
        }
        recorder.record(new Measurement(
                operation.id(),
                stopwatch.subject(),
                stopwatch.event(),
                stoppedAtNanos - stopwatch.startedAtNanos(),
                capturedAt));
    }
}
