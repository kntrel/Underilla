package com.kntrel.mc.underilla.core.profiling;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The elapsed time of one named event for an instrumented subject. */
public record Measurement(UUID operationId, Class<?> subject, String event, long durationNanos, Instant capturedAt) {

    public Measurement {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (event.isBlank()) {
            throw new IllegalArgumentException("event must not be blank");
        }
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
    }
}
