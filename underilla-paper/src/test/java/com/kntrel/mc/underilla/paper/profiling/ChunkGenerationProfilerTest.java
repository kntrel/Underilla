package com.kntrel.mc.underilla.paper.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.profiling.Measurement;
import com.kntrel.mc.underilla.core.profiling.Stopwatch;
import com.kntrel.mc.underilla.core.profiling.Tracker;
import com.kntrel.mc.underilla.paper.generation.UnderillaChunkGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ChunkGenerationProfilerTest {

    private static final UUID WORLD_ID = UUID.fromString("f63715e2-0fed-4f3f-b432-a734a26d46e7");

    @Test
    void accumulatesActiveStepTimeAndPreservesTheOperationAcrossIdleGaps() {
        AtomicLong nanoTime = new AtomicLong(100);
        List<Measurement> measurements = new ArrayList<>();
        Instrumenter instrumenter = new Instrumenter(measurements::add);
        ChunkGenerationProfiler profiler = new ChunkGenerationProfiler(instrumenter, nanoTime::get);
        Tracker nestedTracker = instrumenter.tracker(ChunkGenerationProfilerTest.class);

        profiler.run(WORLD_ID, 4, -3, () -> {
            Stopwatch nested = nestedTracker.stopwatch("surface_patch");
            nested.stop();
            nanoTime.set(115);
        });
        nanoTime.set(1_000);
        profiler.run(WORLD_ID, 4, -3, () -> nanoTime.set(1_025));
        profiler.complete(WORLD_ID, 4, -3);

        assertEquals(2, measurements.size());
        Measurement nested = measurements.get(0);
        Measurement chunk = measurements.get(1);
        assertEquals(nested.operationId(), chunk.operationId());
        assertEquals(UnderillaChunkGenerator.class, chunk.subject());
        assertEquals(ChunkGenerationProfiler.CHUNK_GENERATION_EVENT, chunk.event());
        assertEquals(40, chunk.durationNanos());
        assertEquals(0, profiler.activeProfiles());
    }

    @Test
    void overlappingCallbacksCountAsOneActiveInterval() throws Exception {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        ChunkGenerationProfiler profiler = new ChunkGenerationProfiler(
                new Instrumenter(measurements::add),
                nanoTime::get);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);
        CountDownLatch finishSecond = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> profiler.run(WORLD_ID, 1, 2, () -> {
            firstStarted.countDown();
            await(finishFirst);
        }));
        firstStarted.await(5, TimeUnit.SECONDS);

        nanoTime.set(10);
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> profiler.run(WORLD_ID, 1, 2, () -> {
            secondStarted.countDown();
            await(finishSecond);
        }));
        secondStarted.await(5, TimeUnit.SECONDS);

        nanoTime.set(30);
        finishFirst.countDown();
        first.get(5, TimeUnit.SECONDS);
        nanoTime.set(50);
        finishSecond.countDown();
        second.get(5, TimeUnit.SECONDS);
        profiler.complete(WORLD_ID, 1, 2);

        assertEquals(1, measurements.size());
        assertEquals(50, measurements.getFirst().durationNanos());
    }

    @Test
    void completionWaitsForAnActiveCallback() throws Exception {
        AtomicLong nanoTime = new AtomicLong(20);
        List<Measurement> measurements = new ArrayList<>();
        ChunkGenerationProfiler profiler = new ChunkGenerationProfiler(
                new Instrumenter(measurements::add),
                nanoTime::get);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);

        CompletableFuture<Void> callback = CompletableFuture.runAsync(() -> profiler.run(WORLD_ID, 7, 8, () -> {
            started.countDown();
            await(finish);
        }));
        started.await(5, TimeUnit.SECONDS);

        profiler.complete(WORLD_ID, 7, 8);
        assertEquals(0, measurements.size());
        assertEquals(1, profiler.activeProfiles());

        nanoTime.set(55);
        finish.countDown();
        callback.get(5, TimeUnit.SECONDS);

        assertEquals(1, measurements.size());
        assertEquals(35, measurements.getFirst().durationNanos());
        assertEquals(0, profiler.activeProfiles());
    }

    @Test
    void failedCallbackDiscardsItsChunkProfile() {
        AtomicLong nanoTime = new AtomicLong();
        List<Measurement> measurements = new ArrayList<>();
        ChunkGenerationProfiler profiler = new ChunkGenerationProfiler(
                new Instrumenter(measurements::add),
                nanoTime::get);

        assertThrows(IllegalStateException.class,
                () -> profiler.run(WORLD_ID, 9, 10, () -> { throw new IllegalStateException("failed"); }));
        profiler.complete(WORLD_ID, 9, 10);

        assertEquals(0, profiler.activeProfiles());
        assertEquals(List.of(), measurements);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for test latch", exception);
        }
    }
}
