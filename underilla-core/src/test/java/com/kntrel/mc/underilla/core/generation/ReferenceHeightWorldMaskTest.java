package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReferenceHeightWorldMaskTest {

    @Test
    void scansEachNegativeColumnOnlyOnceAcrossAllYValues() {
        CountingWorld world = new CountingWorld();
        WorldMask mask = mask(world, new ChunkCache(1));
        assertFalse(mask.contains(-1, 8, -1));
        int scanReads = world.reads.get();
        assertTrue(scanReads > 1);
        for (int y = -64; y <= 320; y++) {
            assertEquals(y > 8, mask.contains(-1, y, -1));
        }
        assertEquals(scanReads, world.reads.get());
        // Both columns belong to chunk (-1, -1), and must not collide.
        assertFalse(mask.contains(-16, 3, -16));
        int secondScanReads = world.reads.get();
        assertTrue(secondScanReads > scanReads);
        assertTrue(mask.contains(-16, 4, -16));
        assertTrue(mask.contains(-1, 9, -1));
        assertEquals(secondScanReads, world.reads.get());
    }

    @Test
    void otherTopicsPromoteAndEvictTheBoundaryTopicWithTheirChunk() {
        CountingWorld world = new CountingWorld();
        ChunkCache cache = new ChunkCache(2);
        WorldMask mask = mask(world, cache);
        mask.contains(0, 9, 0);
        cache.put(0, 0, "retain this chunk");
        mask.contains(16, 9, 0);
        int reads = world.reads.get();
        cache.get(0, 0, String.class);
        cache.put(2, 0, "evict chunk 1");
        mask.contains(0, 8, 0);
        assertEquals(reads, world.reads.get());
        mask.contains(16, 9, 0);
        assertTrue(world.reads.get() > reads);
    }

    private static WorldMask mask(CountingWorld world, ChunkCache cache) {
        return new ReferenceHeightWorldMask(world, world.air, -64, 320, 20,
                2, 0, 0, _ -> false, _ -> false, cache);
    }

    private static final class CountingWorld implements WorldReader {
        private final Block air = TestBlock.air("minecraft:air");
        private final Block stone = TestBlock.solid("minecraft:stone");
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public Optional<Block> blockAt(int x, int y, int z) {
            reads.incrementAndGet();
            return Optional.of(y <= (x == -16 && z == -16 ? 5 : 10) ? stone : air);
        }

        @Override
        public Optional<Biome> biomeAt(int x, int y, int z) { return Optional.empty(); }

        @Override
        public Optional<ChunkReader> readChunk(int x, int z) { return Optional.empty(); }
    }
}
