package com.kntrel.mc.underilla.core.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kntrel.mc.underilla.core.impl.TestBiome;
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
        ChunkPatcher writesReferenceTerrain = target -> {
            target.setRegion(0, 1, 0, 2, 2, 1, REFERENCE);
            target.setBlock(2, 1, 0, OTHER_REFERENCE);
        };
        DeferredPatcher deferred = new DeferredPatcher(writesReferenceTerrain,
                (position, ignored) -> position.x() == 0);
        ChunkPatcher restore = deferred.applier();

        deferred.patch(chunk);

        assertSame(GENERATED, chunk.getBlock(0, 1, 0));
        assertSame(REFERENCE, chunk.getBlock(1, 1, 0));
        assertSame(OTHER_REFERENCE, chunk.getBlock(2, 1, 0));

        restore.patch(chunk);

        assertSame(REFERENCE, chunk.getBlock(0, 1, 0));
        assertSame(REFERENCE, chunk.getBlock(1, 1, 0));
        assertSame(OTHER_REFERENCE, chunk.getBlock(2, 1, 0));

        chunk.setBlock(0, 1, 0, GENERATED);
        chunk.setBlock(1, 1, 0, GENERATED);
        chunk.setBlock(2, 1, 0, GENERATED);
        restore.patch(chunk);
        assertSame(REFERENCE, chunk.getBlock(0, 1, 0));
        assertSame(GENERATED, chunk.getBlock(1, 1, 0));
        assertSame(GENERATED, chunk.getBlock(2, 1, 0));
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
        }, (position, ignored) -> true);

        assertThrows(IllegalStateException.class, () -> deferred.patch(chunk));
        deferred.applier().patch(chunk);

        assertSame(REFERENCE, chunk.getBlock(0, 1, 0));
    }

    @Test
    void recomputedCoordinateReplacesItsBatchAndMovesToTheEndOfTheFifoCache() {
        TestChunkGrid first = new TestChunkGrid(1, 0, 0, 4, GENERATED, PLAINS);
        TestChunkGrid second = new TestChunkGrid(2, 0, 0, 4, GENERATED, PLAINS);
        TestChunkGrid third = new TestChunkGrid(3, 0, 0, 4, GENERATED, PLAINS);
        AtomicInteger firstRuns = new AtomicInteger();
        AtomicInteger secondRuns = new AtomicInteger();
        ChunkPatcher delegate = target -> {
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
        DeferredPatcher deferred = new DeferredPatcher(delegate, (position, ignored) -> true, 2);

        deferred.patch(first);
        deferred.patch(second);
        deferred.patch(first);
        deferred.patch(third);

        deferred.applier().patch(first);
        assertSame(OTHER_REFERENCE, first.getBlock(0, 1, 0));
        assertEquals(2, firstRuns.get());

        deferred.applier().patch(second);
        assertEquals(2, secondRuns.get());
    }

    @Test
    void rejectsANonPositiveCacheCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeferredPatcher(_ -> {}, (position, ignored) -> true, 0));
    }

    private static TestChunkGrid chunk() {
        return new TestChunkGrid(4, -2, 0, 4, GENERATED, PLAINS);
    }
}
