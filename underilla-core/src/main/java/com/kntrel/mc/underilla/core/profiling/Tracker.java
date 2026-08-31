package com.kntrel.mc.underilla.core.profiling;

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
        Objects.requireNonNull(event, "event");
        if (event.isBlank()) {
            throw new IllegalArgumentException("event must not be blank");
        }
        return instrumenter.stopwatch(subject, event);
    }
}
