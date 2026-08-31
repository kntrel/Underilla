package com.kntrel.mc.underilla.core.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InstrumenterTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CAPTURED_AT, ZoneOffset.UTC);
    private static final UUID OPERATION_ID = UUID.fromString("1839aee4-9be1-4f8d-b7c6-f32e8d76f91d");
    private static final UUID NEXT_OPERATION_ID = UUID.fromString("1d36dbb9-2f43-49fd-839b-7d31e2e70bb7");
    private static final UUID THIRD_OPERATION_ID = UUID.fromString("eb0c25e1-f828-42e5-aef5-df88bc842b20");

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
    void provisionedOperationSharesItsIdentityAcrossRunsAndIdleGaps() {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        Queue<UUID> operationIds = new ArrayDeque<>(List.of(OPERATION_ID, NEXT_OPERATION_ID));
        Instrumenter instrumenter = new Instrumenter(measurements::add, nanoTime::get, CLOCK, operationIds::remove);
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);
        Operation operation = instrumenter.operation();

        operation.run(() -> {
            Stopwatch first = tracker.stopwatch("first_patch");
            nanoTime.set(10);
            first.stop();

            nanoTime.set(20);
            Stopwatch second = tracker.stopwatch("second_patch");
            nanoTime.set(30);
            second.stop();
        });
        String result = operation.call(() -> {
            nanoTime.set(40);
            Stopwatch third = tracker.stopwatch("third_patch");
            nanoTime.set(50);
            third.stop();
            return "done";
        });

        Stopwatch implicit = tracker.stopwatch("implicit_patch");
        nanoTime.set(60);
        implicit.stop();

        assertEquals("done", result);
        assertEquals(OPERATION_ID, operation.id());
        assertEquals(List.of(OPERATION_ID, OPERATION_ID, OPERATION_ID, NEXT_OPERATION_ID),
                measurements.stream().map(Measurement::operationId).toList());
    }

    @Test
    void nestedProvisionedOperationRestoresTheOuterOperation() {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        Queue<UUID> operationIds = new ArrayDeque<>(List.of(OPERATION_ID, NEXT_OPERATION_ID));
        Instrumenter instrumenter = new Instrumenter(measurements::add, nanoTime::get, CLOCK, operationIds::remove);
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);
        Operation outer = instrumenter.operation();
        Operation inner = instrumenter.operation();

        outer.run(() -> {
            Stopwatch before = tracker.stopwatch("before");
            before.stop();

            inner.run(() -> {
                Stopwatch nested = tracker.stopwatch("nested");
                nested.stop();
            });

            Stopwatch after = tracker.stopwatch("after");
            after.stop();
        });

        assertEquals(List.of(OPERATION_ID, NEXT_OPERATION_ID, OPERATION_ID),
                measurements.stream().map(Measurement::operationId).toList());
    }

    @Test
    void provisionedOperationTemporarilyOverridesAnActiveImplicitOperation() {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        Queue<UUID> operationIds = new ArrayDeque<>(List.of(OPERATION_ID, NEXT_OPERATION_ID));
        Instrumenter instrumenter = new Instrumenter(measurements::add, nanoTime::get, CLOCK, operationIds::remove);
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);
        Operation provisioned = instrumenter.operation();
        Stopwatch implicitParent = tracker.stopwatch("implicit_parent");

        provisioned.run(() -> {
            Stopwatch explicitChild = tracker.stopwatch("explicit_child");
            explicitChild.stop();
        });
        Stopwatch implicitContinuation = tracker.stopwatch("implicit_continuation");
        implicitContinuation.stop();
        implicitParent.stop();

        assertEquals(List.of(OPERATION_ID, NEXT_OPERATION_ID, NEXT_OPERATION_ID),
                measurements.stream().map(Measurement::operationId).toList());
    }

    @Test
    void doesNotRestoreAnImplicitOperationThatStoppedInsideAnExplicitScope() {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        Queue<UUID> operationIds = new ArrayDeque<>(List.of(OPERATION_ID, NEXT_OPERATION_ID, THIRD_OPERATION_ID));
        Instrumenter instrumenter = new Instrumenter(measurements::add, nanoTime::get, CLOCK, operationIds::remove);
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);
        Operation provisioned = instrumenter.operation();
        Stopwatch implicitParent = tracker.stopwatch("implicit_parent");

        provisioned.run(() -> {
            implicitParent.stop();
            Stopwatch explicitChild = tracker.stopwatch("explicit_child");
            explicitChild.stop();
        });
        Stopwatch nextImplicit = tracker.stopwatch("next_implicit");
        nextImplicit.stop();

        assertEquals(List.of(NEXT_OPERATION_ID, OPERATION_ID, THIRD_OPERATION_ID),
                measurements.stream().map(Measurement::operationId).toList());
    }

    @Test
    void explicitScopeRestoresThePreviousOperationWhenTheActionFails() {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        Queue<UUID> operationIds = new ArrayDeque<>(List.of(OPERATION_ID, NEXT_OPERATION_ID));
        Instrumenter instrumenter = new Instrumenter(measurements::add, nanoTime::get, CLOCK, operationIds::remove);
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);
        Operation provisioned = instrumenter.operation();

        assertThrows(IllegalStateException.class,
                () -> provisioned.run(() -> { throw new IllegalStateException("failed"); }));
        Stopwatch implicit = tracker.stopwatch("implicit");
        implicit.stop();

        assertEquals(List.of(NEXT_OPERATION_ID),
                measurements.stream().map(Measurement::operationId).toList());
    }

    @Test
    void provisionedOperationCanBeReboundOnAnotherThread() {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        Instrumenter instrumenter = instrumenter(measurements, nanoTime);
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);
        Operation operation = instrumenter.operation();

        CompletableFuture.runAsync(() -> operation.run(() -> {
            Stopwatch asynchronous = tracker.stopwatch("asynchronous");
            asynchronous.stop();
        })).join();
        operation.run(() -> {
            Stopwatch continuation = tracker.stopwatch("continuation");
            continuation.stop();
        });

        assertEquals(List.of(OPERATION_ID, OPERATION_ID),
                measurements.stream().map(Measurement::operationId).toList());
    }

    @Test
    void recordsAnExternallyMeasuredDurationInsideTheCurrentOperation() {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        Instrumenter instrumenter = instrumenter(measurements, nanoTime);
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);
        Operation operation = instrumenter.operation();

        operation.run(() -> tracker.record("chunk_generation", Duration.ofNanos(123)));

        assertEquals(List.of(new Measurement(
                OPERATION_ID, InstrumenterTest.class, "chunk_generation", 123, CAPTURED_AT)), measurements);
    }

    @Test
    void externallyMeasuredDurationUsesAStandaloneOperationOutsideAScope() {
        List<Measurement> measurements = new ArrayList<>();
        Instrumenter instrumenter = instrumenter(measurements, new AtomicLong());

        instrumenter.tracker(InstrumenterTest.class).record("chunk_generation", Duration.ZERO);

        assertEquals(List.of(new Measurement(
                OPERATION_ID, InstrumenterTest.class, "chunk_generation", 0, CAPTURED_AT)), measurements);
    }

    @Test
    void rejectsMissingSubjectAndInvalidEventNames() {
        Instrumenter instrumenter = new Instrumenter(measurement -> {});

        assertThrows(NullPointerException.class, () -> instrumenter.tracker(null));
        Tracker tracker = instrumenter.tracker(InstrumenterTest.class);
        assertThrows(NullPointerException.class, () -> tracker.stopwatch(null));
        assertThrows(IllegalArgumentException.class, () -> tracker.stopwatch("  "));
        assertThrows(NullPointerException.class, () -> tracker.record("chunk_generation", null));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.record("chunk_generation", Duration.ofNanos(-1)));
        assertThrows(NullPointerException.class, () -> tracker.record(null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> tracker.record("  ", Duration.ZERO));
    }

    private static Instrumenter instrumenter(List<Measurement> measurements, AtomicLong nanoTime) {
        return new Instrumenter(measurements::add, nanoTime::get, CLOCK, () -> OPERATION_ID);
    }
}
