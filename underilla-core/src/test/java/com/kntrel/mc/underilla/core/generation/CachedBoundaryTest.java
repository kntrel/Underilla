package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kntrel.mc.underilla.core.api.GenerationConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachedBoundaryTest {

    @Test
    void calculatesAndCachesTheWholeChunkOnFirstLookup() {
        AtomicInteger calculations = new AtomicInteger();
        Boundary delegate = (x, z) -> {
            calculations.incrementAndGet();
            return x - z;
        };
        Boundary cache = new CachedBoundary(delegate, 2);

        assertEquals(-2, cache.at(5, 7));
        assertEquals(GenerationConstants.CHUNK_SIZE * GenerationConstants.CHUNK_SIZE, calculations.get());

        assertEquals(15, cache.at(15, 0));
        assertEquals(GenerationConstants.CHUNK_SIZE * GenerationConstants.CHUNK_SIZE, calculations.get());
    }

    @Test
    void promotesAccessedBucketsAndEvictsTheLeastRecentlyUsedChunk() {
        Map<ChunkCoordinate, Integer> calculations = new HashMap<>();
        Boundary delegate = (x, z) -> {
            ChunkCoordinate chunk = chunkAt(x, z);
            calculations.merge(chunk, 1, Integer::sum);
            return x + z;
        };
        Boundary cache = new CachedBoundary(delegate, 2);
        ChunkCoordinate first = new ChunkCoordinate(0, 0);
        ChunkCoordinate second = new ChunkCoordinate(1, 0);
        ChunkCoordinate third = new ChunkCoordinate(2, 0);

        cache.at(0, 0);
        cache.at(16, 0);
        cache.at(1, 0); // Promote the first chunk, making the second chunk least recently used.
        cache.at(32, 0);

        assertEquals(256, calculations.get(first));
        assertEquals(256, calculations.get(second));
        assertEquals(256, calculations.get(third));

        cache.at(2, 0);
        assertEquals(256, calculations.get(first));

        cache.at(16, 0);
        assertEquals(512, calculations.get(second));
    }

    @Test
    void usesFloorCoordinatesForNegativeChunks() {
        AtomicInteger calculations = new AtomicInteger();
        Boundary delegate = (x, z) -> {
            calculations.incrementAndGet();
            return x * 100 + z;
        };
        Boundary cache = new CachedBoundary(delegate, 1);

        assertEquals(-101, cache.at(-1, -1));
        assertEquals(256, calculations.get());

        assertEquals(-1616, cache.at(-16, -16));
        assertEquals(256, calculations.get());
    }

    @Test
    void requiresAtLeastOneChunkOfCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new CachedBoundary((x, z) -> 0, 0));
    }

    private static ChunkCoordinate chunkAt(int globalX, int globalZ) {
        return new ChunkCoordinate(Math.floorDiv(globalX, GenerationConstants.CHUNK_SIZE),
                Math.floorDiv(globalZ, GenerationConstants.CHUNK_SIZE));
    }

    private record ChunkCoordinate(int x, int z) {}
}
