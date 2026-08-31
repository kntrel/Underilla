package com.kntrel.mc.underilla.core.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InstrumenterTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CAPTURED_AT, ZoneOffset.UTC);
    private static final UUID OPERATION_ID = UUID.fromString("1839aee4-9be1-4f8d-b7c6-f32e8d76f91d");
    private static final UUID NEXT_OPERATION_ID = UUID.fromString("1d36dbb9-2f43-49fd-839b-7d31e2e70bb7");

    @Test
    void stoppedStopwatchRoutesMeasurementToRecorder() {
        AtomicLong nanoTime = new AtomicLong(100);
        List<Measurement> measurements = new ArrayList<>();
        Instrumenter instrumenter = instrumenter(measurements, nanoTime);

        Stopwatch stopwatch = instrumenter.tracker(InstrumenterTest.class).stopwatch("surface_patch");
        nanoTime.set(145);
        stopwatch.stop();

        assertEquals(List.of(new Measurement(
                OPERATION_ID, InstrumenterTest.class, "surface_patch", 45, CAPTURED_AT)), measurements);
    }

    @Test
    void stopwatchRecordsOnlyOnce() {
        AtomicLong nanoTime = new AtomicLong(10);
        List<Measurement> measurements = new ArrayList<>();
        Instrumenter instrumenter = instrumenter(measurements, nanoTime);
        Stopwatch stopwatch = instrumenter.tracker(InstrumenterTest.class).stopwatch("surface_patch");

        nanoTime.set(20);
        stopwatch.stop();
        nanoTime.set(30);
        stopwatch.stop();
        stopwatch.close();

        assertEquals(List.of(new Measurement(
                OPERATION_ID, InstrumenterTest.class, "surface_patch", 10, CAPTURED_AT)), measurements);
    }

    @Test
    void closingStopwatchRecordsMeasurement() {
        AtomicLong nanoTime = new AtomicLong(5);
        List<Measurement> measurements = new ArrayList<>();
        Instrumenter instrumenter = instrumenter(measurements, nanoTime);

        try (Stopwatch ignored = instrumenter.tracker(InstrumenterTest.class).stopwatch("surface_patch")) {
            nanoTime.set(12);
        }

        assertEquals(List.of(new Measurement(
                OPERATION_ID, InstrumenterTest.class, "surface_patch", 7, CAPTURED_AT)), measurements);
    }

    @Test
    void overlappingStopwatchesStopImmediatelyAndShareTheirOperation() {
        AtomicLong nanoTime = new AtomicLong(100);
        List<Measurement> measurements = new ArrayList<>();
        Instrumenter instrumenter = instrumenter(measurements, nanoTime);
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);

        Stopwatch parent = tracker.stopwatch("surface_patch");
        nanoTime.set(110);
        Stopwatch child = tracker.stopwatch("caves_patch");

        nanoTime.set(120);
        parent.stop();
        assertEquals(List.of(
                new Measurement(OPERATION_ID, InstrumenterTest.class, "surface_patch", 20, CAPTURED_AT)
        ), measurements);

        nanoTime.set(125);
        Stopwatch continuation = tracker.stopwatch("liquid_patch");
        nanoTime.set(130);
        continuation.stop();

        nanoTime.set(140);
        child.stop();

        assertEquals(List.of(
                new Measurement(OPERATION_ID, InstrumenterTest.class, "surface_patch", 20, CAPTURED_AT),
                new Measurement(OPERATION_ID, InstrumenterTest.class, "liquid_patch", 5, CAPTURED_AT),
                new Measurement(OPERATION_ID, InstrumenterTest.class, "caves_patch", 30, CAPTURED_AT)
        ), measurements);
    }

    @Test
    void emptyStopwatchStackDropsTheCurrentOperation() {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        Queue<UUID> operationIds = new ArrayDeque<>(List.of(OPERATION_ID, NEXT_OPERATION_ID));
        Instrumenter instrumenter = new Instrumenter(measurements::add, nanoTime::get, CLOCK, operationIds::remove);
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);

        Stopwatch first = tracker.stopwatch("first_patch");
        nanoTime.set(10);
        first.stop();
        Stopwatch second = tracker.stopwatch("second_patch");
        nanoTime.set(20);
        second.stop();

        assertEquals(List.of(OPERATION_ID, NEXT_OPERATION_ID),
                measurements.stream().map(Measurement::operationId).toList());
    }

    @Test
    void rejectsMissingSubjectAndInvalidEventNames() {
        Instrumenter instrumenter = new Instrumenter(measurement -> {});

        assertThrows(NullPointerException.class, () -> instrumenter.tracker(null));
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);
        assertThrows(NullPointerException.class, () -> tracker.stopwatch(null));
        assertThrows(IllegalArgumentException.class, () -> tracker.stopwatch("  "));
    }

    private static Instrumenter instrumenter(List<Measurement> measurements, AtomicLong nanoTime) {
        return new Instrumenter(measurements::add, nanoTime::get, CLOCK, () -> OPERATION_ID);
    }
}
