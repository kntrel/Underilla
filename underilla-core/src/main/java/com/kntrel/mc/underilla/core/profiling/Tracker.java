package com.kntrel.mc.underilla.core.profiling;

import java.time.Duration;
import java.util.Objects;

/** Starts named measurements for one instrumented subject. */
public final class Tracker {

    private final Instrumenter instrumenter;
    private final Class<?> subject;

    Tracker(Instrumenter instrumenter, Class<?> subject) {
        this.instrumenter = Objects.requireNonNull(instrumenter, "instrumenter");
        this.subject = Objects.requireNonNull(subject, "subject");
    }

    /** Starts a stopwatch for a named event. */
    public Stopwatch stopwatch(String event) {
        return instrumenter.stopwatch(subject, requireEvent(event));
    }

    /** Records an elapsed duration that was measured outside the instrumenter. */
    public void record(String event, Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        instrumenter.record(subject, requireEvent(event), duration.toNanos());
    }

    private static String requireEvent(String event) {
        Objects.requireNonNull(event, "event");
        if (event.isBlank()) {
            throw new IllegalArgumentException("event must not be blank");
        }
        return event;
    }
}
