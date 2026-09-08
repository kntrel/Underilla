package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachedWorldMaskTest {

    @Test
    void calculatesEachPositionOnlyOnce() {
        AtomicInteger calculations = new AtomicInteger();
        WorldMask delegate = (x, y, z) -> {
            calculations.incrementAndGet();
            return x == y && y == z;
        };
        WorldMask cache = new CachedWorldMask(delegate, 2);

        assertTrue(cache.contains(5, 5, 5));
        assertTrue(cache.contains(5, 5, 5));
        assertFalse(cache.contains(5, 6, 5));
        assertFalse(cache.contains(5, 6, 5));

        assertEquals(2, calculations.get());
    }

    @Test
    void promotesAccessedBucketsAndEvictsTheLeastRecentlyUsedChunk() {
        Map<ChunkCoordinate, Integer> calculations = new HashMap<>();
        WorldMask delegate = (x, y, z) -> {
            calculations.merge(chunkAt(x, z), 1, Integer::sum);
            return true;
        };
        WorldMask cache = new CachedWorldMask(delegate, 2);
        ChunkCoordinate first = new ChunkCoordinate(0, 0);
        ChunkCoordinate second = new ChunkCoordinate(1, 0);
        ChunkCoordinate third = new ChunkCoordinate(2, 0);

        cache.contains(0, 0, 0);
        cache.contains(16, 0, 0);
        cache.contains(1, 0, 0);
        cache.contains(32, 0, 0);
        cache.contains(0, 0, 0);
        cache.contains(16, 0, 0);

        assertEquals(2, calculations.get(first));
        assertEquals(2, calculations.get(second));
        assertEquals(1, calculations.get(third));
    }

    @Test
    void usesFloorCoordinatesForNegativeChunksAndSections() {
        AtomicInteger calculations = new AtomicInteger();
        WorldMask delegate = (x, y, z) -> {
            calculations.incrementAndGet();
            return x == -1 && y == -1 && z == -1;
        };
        WorldMask cache = new CachedWorldMask(delegate, 1);

        assertTrue(cache.contains(-1, -1, -1));
        assertTrue(cache.contains(-1, -1, -1));

        assertEquals(1, calculations.get());
    }

    @Test
    void requiresAtLeastOneChunkOfCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new CachedWorldMask((_, _, _) -> false, 0));
    }

    private static ChunkCoordinate chunkAt(int globalX, int globalZ) {
        return new ChunkCoordinate(Math.floorDiv(globalX, 16), Math.floorDiv(globalZ, 16));
    }

    private record ChunkCoordinate(int x, int z) {}
}
