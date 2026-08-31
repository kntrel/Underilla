package com.kntrel.mc.underilla.core.profiling;

import java.util.Objects;

/** A running event measurement that records its elapsed time when stopped. */
public final class Stopwatch implements AutoCloseable {

    private final Instrumenter instrumenter;
    private final Operation operation;
    private final Class<?> subject;
    private final String event;
    private final long startedAtNanos;
    private final Thread owner;
    private boolean stopped;

    Stopwatch(Instrumenter instrumenter, Operation operation, Class<?> subject, String event, long startedAtNanos) {
        this.instrumenter = Objects.requireNonNull(instrumenter, "instrumenter");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.event = Objects.requireNonNull(event, "event");
        this.startedAtNanos = startedAtNanos;
        this.owner = Thread.currentThread();
    }

    /** Stops this stopwatch and records its measurement. Subsequent calls have no effect. */
    public void stop() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("stopwatch must be stopped on the thread where it was started");
        }
        if (!stopped) {
            instrumenter.stop(this);
        }
    }

    Operation operation() {
        return operation;
    }

    Class<?> subject() {
        return subject;
    }

    String event() {
        return event;
    }

    long startedAtNanos() {
        return startedAtNanos;
    }

    void markStopped() {
        stopped = true;
    }

    /** Stops this stopwatch, allowing it to be used with try-with-resources. */
    @Override
    public void close() {
        stop();
    }
}
