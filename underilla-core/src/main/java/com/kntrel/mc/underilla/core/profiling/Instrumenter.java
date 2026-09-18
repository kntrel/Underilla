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
    private final ThreadLocal<OperationBinding> currentOperation = new ThreadLocal<>();

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

    /** Provisions an operation that can be explicitly rebound across separate units of work. */
    public Operation operation() {
        return new Operation(this, operationIds.get());
    }

    void run(Operation operation, Runnable action) {
        Objects.requireNonNull(action, "action");
        call(operation, () -> {
            action.run();
            return null;
        });
    }

    <T> T call(Operation operation, Supplier<T> action) {
        Objects.requireNonNull(operation, "operation").requireOwner(this);
        Objects.requireNonNull(action, "action");

        OperationBinding previous = currentOperation.get();
        currentOperation.set(new OperationBinding(operation, true));
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    Stopwatch stopwatch(Class<?> subject, String event) {
        OperationBinding binding = currentOperation.get();
        if (binding == null) {
            binding = new OperationBinding(operation(), false);
            currentOperation.set(binding);
        }

        Operation operation = binding.operation();
        Stopwatch stopwatch = new Stopwatch(this, operation, subject, event, nanoTime.getAsLong());
        operation.add(stopwatch);
        return stopwatch;
    }

    void record(Class<?> subject, String event, long durationNanos) {
        OperationBinding binding = currentOperation.get();
        Operation operation = binding == null ? operation() : binding.operation();
        recorder.record(new Measurement(
                operation.id(),
                subject,
                event,
                durationNanos,
                Instant.now(clock)));
    }

    void stop(Stopwatch stopwatch) {
        Operation operation = stopwatch.operation();
        long stoppedAtNanos = nanoTime.getAsLong();
        Instant capturedAt = Instant.now(clock);
        operation.remove(stopwatch);
        stopwatch.markStopped();

        OperationBinding binding = currentOperation.get();
        if (operation.isEmpty()
                && binding != null
                && binding.operation() == operation
                && !binding.explicit()) {
            currentOperation.remove();
        }
        recorder.record(new Measurement(
                operation.id(),
                stopwatch.subject(),
                stopwatch.event(),
                stoppedAtNanos - stopwatch.startedAtNanos(),
                capturedAt));
    }

    private void restore(OperationBinding previous) {
        if (previous == null || (!previous.explicit() && previous.operation().isEmpty())) {
            currentOperation.remove();
        } else {
            currentOperation.set(previous);
        }
    }

    private record OperationBinding(Operation operation, boolean explicit) {}
}
