package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.World;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.WorldSnapshot;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.kntrel.mc.underilla.core.reference.mask.ReferenceHeightWorldMask;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import org.junit.jupiter.api.Test;

class ReferenceHeightWorldMaskTest {

    @Test
    void worldsWithDifferentSeedsInvalidateCachedBoundaryDecisions() {
        Block air = TestBlock.air("minecraft:air");
        Biome biome = new TestBiome("minecraft:plains");
        TestWorld source = new TestWorld().addChunk(new TestChunkGrid(0, 0, 0, 9, air, biome)
                .fillLayer(5, TestBlock.solid("minecraft:stone")));
        AtomicInteger evaluations = new AtomicInteger();
        WorldMask mask = new ReferenceHeightWorldMask(source, air, 0, 8, 8, 0, 0, 0,
                scope -> {
                    evaluations.incrementAndGet();
                    return scope.target().world().seed() == 2L;
                }, _ -> false, new ChunkCache(1));
        World first = World.of("generated", ID.of("minecraft:overworld"), 0, 16, 1L);
        World second = World.of("generated", ID.of("minecraft:overworld"), 0, 16, 2L);

        assertFalse(mask.contains(first, 0, 1, 0));
        assertTrue(mask.contains(second, 0, 1, 0));
        assertFalse(mask.contains(first, 0, 1, 0));
        assertEquals(3, evaluations.get());
    }

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
    void worldMetadataSurvivesMaskCompositionAndTargetChangesInvalidateCachedBoundaries() {
        Block air = TestBlock.air("minecraft:air");
        Block stone = TestBlock.solid("minecraft:stone");
        Biome biome = new TestBiome("example:source");
        World sourceWorld = World.of("reference", ID.of("minecraft:overworld"), 0, 16);
        World nether = World.of("nether", ID.of("minecraft:the_nether"), 0, 256);
        World overworld = World.of("overworld", ID.of("minecraft:overworld"), -64, 320);
        TestChunkGrid source = new TestChunkGrid(0, 0, 0, 9, air, biome).fillLayer(5, stone);
        TestWorld reader = new TestWorld().addChunk(source).withWorld(sourceWorld);
        WorldMask mask = new ReferenceHeightWorldMask(reader, air, 0, 8, 8, 0, 0, 0,
                snapshot -> {
                    assertEquals(WorldSnapshot.of(reader), snapshot.source().world());
                    return snapshot.target().world().dimension().equals(ID.of("minecraft:the_nether"));
                }, _ -> false, new ChunkCache(1));
        WorldMask composed = new com.kntrel.mc.underilla.core.reference.mask.UnionWorldMask(
                mask.inverted(), (_, _, _) -> false);

        assertFalse(composed.contains(nether, 0, 1, 0));
        assertTrue(composed.contains(overworld, 0, 1, 0));
        assertFalse(composed.contains(nether, 0, 1, 0));
    }

    @Test
    void sourceBoundaryRulesCanReadTheSourceBlockBiomeAndChunk() {
        Block air = TestBlock.air("minecraft:air");
        Block stone = TestBlock.solid("minecraft:stone");
        Block ignored = TestBlock.solid("example:ignored");
        Biome biome = new TestBiome("example:source");
        TestChunkGrid source = new TestChunkGrid(-1, -2, 0, 9, air, biome);
        source.fillLayer(3, stone);
        source.fillLayer(5, ignored);
        TestWorld world = new TestWorld().addChunk(source);
        WorldMask mask = new ReferenceHeightWorldMask(world, air, 0, 8, 8, 0, 0, 0,
                snapshot -> {
                    assertEquals(biome.id(), snapshot.source().biome().id());
                    assertNull(snapshot.target().biome());
                    return false;
                },
                snapshot -> {
                    assertEquals(WorldSnapshot.of(world), snapshot.world());
                    assertEquals(biome.id(), snapshot.biome().id());
                    assertEquals(-1, snapshot.chunk().x());
                    assertEquals(-2, snapshot.chunk().z());
                    assertEquals(-1, snapshot.block().x());
                    assertEquals(-17, snapshot.block().z());
                    assertEquals(-1, snapshot.biome().cellX());
                    assertEquals(Math.floorDiv(snapshot.block().y(), 4), snapshot.biome().cellY());
                    assertEquals(-5, snapshot.biome().cellZ());
                    return snapshot.block().id().equals(ignored.id());
                }, new ChunkCache(1));

        assertFalse(mask.contains(-1, 3, -17));
        assertTrue(mask.contains(-1, 4, -17));
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
        @Override public String name() { return "test-world"; }
        @Override public com.kntrel.mc.underilla.core.api.ID dimension() { return com.kntrel.mc.underilla.core.api.ID.of("minecraft:overworld"); }
        @Override public long seed() { return 0L; }
        @Override public int minHeight() { return -64; }
        @Override public int maxHeight() { return 320; }

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
