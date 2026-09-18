package com.kntrel.mc.underilla.paper.profiling;

import java.util.Objects;

/** Identifies one stream of measurements by subject and event name. */
public record MetricKey(String subject, String event) {

    public MetricKey {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(event, "event");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (event.isBlank()) {
            throw new IllegalArgumentException("event must not be blank");
        }
    }
}
