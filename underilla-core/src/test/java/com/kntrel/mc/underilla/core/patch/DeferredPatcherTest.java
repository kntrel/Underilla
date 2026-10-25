package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.ChunkData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DeferredPatcherTest {

    private static final TestBlock GENERATED = TestBlock.solid("minecraft:generated");
    private static final TestBlock REFERENCE = TestBlock.solid("minecraft:reference");
    private static final TestBlock OTHER_REFERENCE = TestBlock.solid("minecraft:other_reference");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");

    @Test
    void usesTheCachedBatchThenRecomputesOnlySelectedWritesOnAMiss() {
        TestChunkGrid chunk = new TestChunkGrid(4, -2, 0, 4, GENERATED, PLAINS);
        Patcher<ChunkData> writesReferenceTerrain = target -> {
            target.setRegion(0, 1, 0, 2, 2, 1, REFERENCE);
            target.setBlock(2, 1, 0, OTHER_REFERENCE);
        };
        DeferredPatcher deferred = new DeferredPatcher(writesReferenceTerrain,
                (position, ignored) -> position.x() == 0, new ChunkCache(2));
        Patcher<ChunkData> restore = deferred.applier();

        deferred.patch(chunk);

        assertEquals(GENERATED.id(), chunk.getBlock(0, 1, 0).id());
        assertEquals(REFERENCE.id(), chunk.getBlock(1, 1, 0).id());
        assertEquals(OTHER_REFERENCE.id(), chunk.getBlock(2, 1, 0).id());

        restore.patch(chunk);

        assertEquals(REFERENCE.id(), chunk.getBlock(0, 1, 0).id());
        assertEquals(REFERENCE.id(), chunk.getBlock(1, 1, 0).id());
        assertEquals(OTHER_REFERENCE.id(), chunk.getBlock(2, 1, 0).id());

        chunk.setBlock(0, 1, 0, GENERATED);
        chunk.setBlock(1, 1, 0, GENERATED);
        chunk.setBlock(2, 1, 0, GENERATED);
        restore.patch(chunk);
        assertEquals(REFERENCE.id(), chunk.getBlock(0, 1, 0).id());
        assertEquals(GENERATED.id(), chunk.getBlock(1, 1, 0).id());
        assertEquals(GENERATED.id(), chunk.getBlock(2, 1, 0).id());
    }

    @Test
    void failedDelegateLeavesAMissForTheApplierToRecompute() {
        TestChunkGrid chunk = chunk();
        AtomicBoolean fail = new AtomicBoolean(true);
        DeferredPatcher deferred = new DeferredPatcher(target -> {
            target.setBlock(0, 1, 0, REFERENCE);
            if (fail.getAndSet(false)) {
                throw new IllegalStateException("generation failed");
            }
        }, (position, ignored) -> true, new ChunkCache(2));

        assertThrows(IllegalStateException.class, () -> deferred.patch(chunk));
        deferred.applier().patch(chunk);

        assertEquals(REFERENCE.id(), chunk.getBlock(0, 1, 0).id());
    }

    @Test
    void recomputedCoordinateReplacesItsBatchAndPromotesTheChunk() {
        TestChunkGrid first = new TestChunkGrid(1, 0, 0, 4, GENERATED, PLAINS);
        TestChunkGrid second = new TestChunkGrid(2, 0, 0, 4, GENERATED, PLAINS);
        TestChunkGrid third = new TestChunkGrid(3, 0, 0, 4, GENERATED, PLAINS);
        AtomicInteger firstRuns = new AtomicInteger();
        AtomicInteger secondRuns = new AtomicInteger();
        Patcher<ChunkData> delegate = target -> {
            if (target.getChunkX() == 1) {
                target.setBlock(0, 1, 0,
                        firstRuns.incrementAndGet() == 1 ? REFERENCE : OTHER_REFERENCE);
            } else if (target.getChunkX() == 2) {
                secondRuns.incrementAndGet();
                target.setBlock(0, 1, 0, REFERENCE);
            } else {
                target.setBlock(0, 1, 0, REFERENCE);
            }
        };
        DeferredPatcher deferred = new DeferredPatcher(delegate, (position, ignored) -> true, new ChunkCache(2));

        deferred.patch(first);
        deferred.patch(second);
        deferred.patch(first);
        deferred.patch(third);

        deferred.applier().patch(first);
        assertEquals(OTHER_REFERENCE.id(), first.getBlock(0, 1, 0).id());
        assertEquals(2, firstRuns.get());

        deferred.applier().patch(second);
        assertEquals(2, secondRuns.get());
    }

    @Test
    void rejectsANullCache() {
        assertThrows(NullPointerException.class,
                () -> new DeferredPatcher(_ -> {}, (position, ignored) -> true, null));
    }

    @Test
    void sharesChunkPromotionAndEvictionWithOtherTopicsAndConsumesOnlyItsOwn() {
        ChunkCache cache = new ChunkCache(2);
        AtomicInteger runs = new AtomicInteger();
        DeferredPatcher deferred = new DeferredPatcher(target -> {
            runs.incrementAndGet();
            target.setBlock(0, 1, 0, REFERENCE);
        }, (position, ignored) -> true, cache);
        TestChunkGrid first = new TestChunkGrid(1, 0, 0, 4, GENERATED, PLAINS);
        TestChunkGrid second = new TestChunkGrid(2, 0, 0, 4, GENERATED, PLAINS);

        deferred.patch(first);
        cache.put(1, 0, "other topic");
        deferred.patch(second);
        cache.get(1, 0, String.class);
        cache.put(3, 0, "evicts second");

        deferred.applier().patch(first);
        assertEquals(2, runs.get());
        assertEquals(REFERENCE.id(), first.getBlock(0, 1, 0).id());
        assertEquals("other topic", cache.get(1, 0, String.class).orElseThrow());

        deferred.applier().patch(second);
        assertEquals(3, runs.get());
        assertEquals(REFERENCE.id(), second.getBlock(0, 1, 0).id());

        deferred.applier().patch(first);
        assertEquals(4, runs.get());
    }

    private static TestChunkGrid chunk() {
        return new TestChunkGrid(4, -2, 0, 4, GENERATED, PLAINS);
    }
}
