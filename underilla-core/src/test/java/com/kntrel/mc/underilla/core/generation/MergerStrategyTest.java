package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jkantrell.nbt.tag.CompoundTag;
import com.jkantrell.nbt.tag.StringTag;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.reference.mask.ReferenceHeightWorldMask;
import com.kntrel.mc.underilla.core.reference.ReferenceWorldPatcher;
import com.kntrel.mc.underilla.core.reference.WorldHeightMaskPatcher;
import com.kntrel.mc.underilla.core.reference.mask.AbsoluteWorldMask;
import com.kntrel.mc.underilla.core.reference.mask.UnionWorldMask;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import com.kntrel.mc.underilla.core.vector.LocatedBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class PatcherStrategyTest {

    private static final TestBlock AIR = new TestBlock("minecraft:air", false, false, true);
    private static final TestBlock STONE = new TestBlock("minecraft:stone", true, false, false);
    private static final TestBlock LEAVES = new TestBlock("minecraft:oak_leaves", true, false, false);
    private static final TestBlock REFERENCE = new TestBlock("minecraft:reference", true, false, false);
    private static final TestBlock GENERATED = new TestBlock("minecraft:generated", true, false, false);
    @Test
    void absoluteWorldMaskExcludesItsBoundary() {
        WorldMask worldMask = new AbsoluteWorldMask(10);

        assertTrue(worldMask.contains(0, 11, 0));
        assertFalse(worldMask.contains(0, 10, 0));
        assertFalse(worldMask.contains(0, 9, 0));
    }

    @Test
    void absolutePatcherUsesOneFixedWorldMask() {
        TestConfig config = new TestConfig();
        config.minimumY = 0;
        config.maximumY = 8;
        config.maximumCaveY = 2;
        FakeChunkReader referenceChunk = FakeChunkReader.filled(0, 0, 5, REFERENCE);
        FakeWorldReader referenceWorld = new FakeWorldReader();
        referenceWorld.putChunk(referenceChunk);
        WorldMask worldMask = new AbsoluteWorldMask(config.maximumCaveY, config.minimumY, config.maximumY);
        Patcher<ChunkData> patcher = referenceWorldPatcher(referenceWorld, worldMask, config);
        FakeChunkData destination = new FakeChunkData(0, 8, 0, 0, GENERATED);

        patcher.patch(destination);

        assertSame(GENERATED, destination.getBlock(0, 2, 0));
        assertSame(REFERENCE, destination.getBlock(0, 3, 0));
        assertSame(REFERENCE, destination.getBlock(0, 4, 0));
        assertSame(AIR, destination.getBlock(0, 5, 0));
        assertFalse(worldMask.contains(100, 2, -100));
        assertTrue(worldMask.contains(100, 3, -100));
    }

    @Test
    void surfaceWorldMaskCalculatesAndOwnsThePerColumnWorldMask() {
        TestConfig config = new TestConfig();
        config.minimumY = 0;
        config.maximumY = 8;
        config.maximumCaveY = 6;
        config.depth = 2;
        config.adaptiveMaximumDepth = 2;
        config.ignoredSurfaceBlocks = Set.of(LEAVES.id());
        FakeWorldReader world = new FakeWorldReader();
        world.putBlock(0, 4, 0, STONE);
        world.putBlock(0, 5, 0, LEAVES);
        world.putBiome(0, config.maximumY, 0, "minecraft:plains");

        WorldMask worldMask = surfaceWorldMask(world, config);

        assertFalse(worldMask.contains(0, 2, 0));
        assertTrue(worldMask.contains(0, 3, 0));
    }

    @Test
    void surfaceWorldMaskPreservesTheWholeReferenceColumnForConfiguredBiomes() {
        TestConfig config = new TestConfig();
        config.minimumY = -64;
        config.maximumY = 320;
        config.maximumCaveY = 200;
        config.preservedBiomes = Set.of(ID.of("example:preserved"));
        FakeWorldReader world = new FakeWorldReader();
        world.putBiome(12, config.maximumY, -4, "example:preserved");

        WorldMask worldMask = surfaceWorldMask(world, config);

        assertFalse(worldMask.contains(12, -64, -4));
        assertTrue(worldMask.contains(12, -63, -4));
    }

    @Test
    void referenceWorldPatcherFillsFromAReferenceSurfaceDownToALowerVanillaSurface() {
        TestConfig config = new TestConfig();
        config.minimumY = 0;
        config.maximumY = 12;
        config.maximumCaveY = 10;
        config.depth = 2;
        FakeChunkReader referenceChunk = FakeChunkReader.filled(0, 0, 11, REFERENCE);
        FakeWorldReader referenceWorld = new FakeWorldReader();
        referenceWorld.putChunk(referenceChunk);
        for (int y = 0; y <= 10; y++) {
            referenceWorld.putBlock(0, y, 0, REFERENCE);
        }
        referenceWorld.fillChunkBiome(config.maximumY, "minecraft:plains");
        FakeChunkData destination = new FakeChunkData(0, 12, 0, 0, AIR);
        for (int y = 0; y <= 5; y++) {
            destination.setBlock(0, y, 0, GENERATED);
        }

        referenceWorldPatcher(referenceWorld, surfaceWorldMask(referenceWorld, config), config).patch(destination);

        assertSame(GENERATED, destination.getBlock(0, 5, 0));
        assertSame(REFERENCE, destination.getBlock(0, 6, 0));
        assertSame(REFERENCE, destination.getBlock(0, 10, 0));
    }

    @Test
    void referenceWorldPatcherCanDisableSurfaceFilling() {
        TestConfig config = new TestConfig();
        config.minimumY = 0;
        config.maximumY = 8;
        FakeChunkReader referenceChunk = FakeChunkReader.filled(0, 0, 7, REFERENCE);
        FakeWorldReader referenceWorld = new FakeWorldReader();
        referenceWorld.putChunk(referenceChunk);
        FakeChunkData destination = new FakeChunkData(0, 8, 0, 0, AIR);
        for (int y = 0; y <= 4; y++) {
            destination.setBlock(0, y, 0, GENERATED);
        }
        WorldMask emptyMask = (_, _, _) -> false;
        BlockFactory blocks = blockFactory();
        Patcher<ChunkData> patcher = new ReferenceWorldPatcher(
                referenceWorld,
                emptyMask,
                config.generationAreaMinY(),
                blocks::air,
                _ -> false,
                UnaryOperator.identity()
        );

        patcher.patch(destination);

        assertSame(AIR, destination.getBlock(0, 5, 0));
    }

    @Test
    void referenceWorldPatcherSupportsNonHeightBasedMasks() {
        TestConfig config = new TestConfig();
        config.minimumY = 0;
        config.maximumY = 4;
        FakeChunkReader referenceChunk = FakeChunkReader.filled(0, 0, 4, REFERENCE);
        FakeWorldReader referenceWorld = new FakeWorldReader();
        referenceWorld.putChunk(referenceChunk);
        FakeChunkData destination = new FakeChunkData(0, 4, 0, 0, GENERATED);
        WorldMask singleBlockMask = (x, y, z) -> x == 0 && y == 1 && z == 0;
        BlockFactory blocks = blockFactory();
        Patcher<ChunkData> patcher = new ReferenceWorldPatcher(
                referenceWorld,
                singleBlockMask,
                config.generationAreaMinY(),
                blocks::air,
                _ -> false,
                UnaryOperator.identity()
        );

        patcher.patch(destination);

        assertSame(GENERATED, destination.getBlock(0, 0, 0));
        assertSame(REFERENCE, destination.getBlock(0, 1, 0));
        assertSame(GENERATED, destination.getBlock(0, 2, 0));
    }

    @Test
    void referenceWorldPatcherStillCutsAtALowerReferenceSurface() {
        TestConfig config = new TestConfig();
        config.minimumY = 0;
        config.maximumY = 12;
        config.maximumCaveY = 10;
        config.depth = 2;
        FakeChunkReader referenceChunk = FakeChunkReader.filled(0, 0, 7, REFERENCE);
        FakeWorldReader referenceWorld = new FakeWorldReader();
        referenceWorld.putChunk(referenceChunk);
        for (int y = 0; y <= 6; y++) {
            referenceWorld.putBlock(0, y, 0, REFERENCE);
        }
        referenceWorld.fillChunkBiome(config.maximumY, "minecraft:plains");
        FakeChunkData destination = new FakeChunkData(0, 12, 0, 0, AIR);
        for (int y = 0; y <= 10; y++) {
            destination.setBlock(0, y, 0, GENERATED);
        }

        referenceWorldPatcher(referenceWorld, surfaceWorldMask(referenceWorld, config), config).patch(destination);

        assertSame(GENERATED, destination.getBlock(0, 4, 0));
        assertSame(REFERENCE, destination.getBlock(0, 5, 0));
        assertSame(REFERENCE, destination.getBlock(0, 6, 0));
        assertSame(AIR, destination.getBlock(0, 7, 0));
    }

    private static BlockFactory blockFactory() {
        return new BlockFactory() {
            @Override
            public Block air() { return AIR; }

            @Override
            public Block create(ID id) { return new TestBlock(id.toString(), true, false, false); }
        };
    }

    private static Patcher<ChunkData> referenceWorldPatcher(
            WorldReader surfaceWorld,
            WorldMask worldMask,
            TestConfig config
    ) {
        BlockFactory blocks = blockFactory();
        return new WorldHeightMaskPatcher(config.generationAreaMinY(), heightMask ->
                new ReferenceWorldPatcher(
                        surfaceWorld,
                        new UnionWorldMask(heightMask, worldMask),
                        config.generationAreaMinY(),
                        blocks::air,
                        block -> config.shouldKeepSurfaceBlockInCaves(block.id()),
                        block -> config.surfaceBlockReplacement(block.id()).map(blocks::create).orElse(block)
                )
        );
    }

    private static WorldMask surfaceWorldMask(WorldReader surfaceWorld, TestConfig config) {
        return new ReferenceHeightWorldMask(surfaceWorld, AIR,
                config.generationAreaMinY(), config.generationAreaMaxY(), config.maxHeightOfCaves(),
                config.mergeDepth(), config.adaptiveMaxMergeDepth(), config.adaptiveMinHiddenBlocksMergeDepth(),
                config::isSurfaceWorldOnlyBiome, config::isIgnoredForSurfaceCalculation, new ChunkCache(2));
    }

    private static final class TestConfig {
        private int minimumY = -64;
        private int maximumY = 320;
        private int maximumCaveY = 320;
        private int depth;
        private int adaptiveMaximumDepth;
        private int adaptiveMinimumHiddenDepth;
        private Set<ID> preservedBiomes = Set.of();
        private Set<ID> ignoredSurfaceBlocks = Set.of();

        public int generationAreaMinY() { return minimumY; }

        public int generationAreaMaxY() { return maximumY; }

        public int maxHeightOfCaves() { return maximumCaveY; }

        public int mergeDepth() { return depth; }

        public int adaptiveMaxMergeDepth() { return adaptiveMaximumDepth; }

        public int adaptiveMinHiddenBlocksMergeDepth() { return adaptiveMinimumHiddenDepth; }

        public boolean isSurfaceWorldOnlyBiome(ID biome) { return preservedBiomes.contains(biome); }

        public boolean isIgnoredForSurfaceCalculation(ID block) { return ignoredSurfaceBlocks.contains(block); }

        public boolean shouldKeepSurfaceBlockInCaves(ID block) { return false; }

        public Optional<ID> surfaceBlockReplacement(ID block) { return Optional.empty(); }
    }

    private static final class TestBlock implements Block {
        private final ID id;
        private final boolean solid;
        private final boolean liquid;
        private final boolean air;
        private TestBlock(String name, boolean solid, boolean liquid, boolean air) {
            this.id = ID.of(name);
            this.solid = solid;
            this.liquid = liquid;
            this.air = air;
        }

        @Override
        public TestBlock clone() {
            return new TestBlock(id.toString(), solid, liquid, air);
        }

        @Override
        public boolean isAir() { return air; }

        @Override
        public boolean isSolid() { return solid; }

        @Override
        public boolean isLiquid() { return liquid; }

        @Override
        public boolean isWaterloggable() { return true; }

        @Override
        public void waterlog() {}

        @Override
        public ID id() { return id; }
    }

    private record Position(int x, int y, int z) {}

    private static final class FakeChunkReader extends ChunkReader {
        private final int chunkX;
        private final int chunkZ;
        private final int airSectionsBottom;
        private final Map<Position, Block> blocks = new HashMap<>();

        private FakeChunkReader(int chunkX, int chunkZ, int airSectionsBottom) {
            super(null);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.airSectionsBottom = airSectionsBottom;
        }

        static FakeChunkReader filled(int chunkX, int chunkZ, int airSectionsBottom, Block block) {
            FakeChunkReader reader = new FakeChunkReader(chunkX, chunkZ, airSectionsBottom);
            for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
                for (int y = 0; y < airSectionsBottom; y++) {
                    for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                        reader.blocks.put(new Position(x, y, z), block);
                    }
                }
            }
            return reader;
        }

        void putBlock(int x, int y, int z, Block block) { blocks.put(new Position(x, y, z), block); }

        @Override
        public int getX() { return chunkX; }

        @Override
        public int getZ() { return chunkZ; }

        @Override
        public int getGlobalX(int localX) { return chunkX * GenerationConstants.CHUNK_SIZE + localX; }

        @Override
        public int getGlobalZ(int localZ) { return chunkZ * GenerationConstants.CHUNK_SIZE + localZ; }

        @Override
        public Optional<Block> blockAt(int x, int y, int z) { return Optional.ofNullable(blocks.get(new Position(x, y, z))); }

        @Override
        public Optional<Biome> biomeAt(int x, int y, int z) { return Optional.empty(); }

        @Override
        public int airSectionsBottom() { return airSectionsBottom; }

        @Override
        public List<LocatedBlock> locationsOf(Predicate<Block> checker) {
            List<LocatedBlock> matches = new ArrayList<>();
            blocks.forEach((position, block) -> {
                if (checker.test(block)) {
                    matches.add(new LocatedBlock(position.x(), position.y(), position.z(), block));
                }
            });
            return matches;
        }

        @Override
        protected Optional<Block> decodeBlockFromTag(CompoundTag tag) { return Optional.empty(); }

        @Override
        public Optional<Block> blockFromTag(CompoundTag tag, CompoundTag entityTag) { return Optional.empty(); }

        @Override
        public Optional<Biome> biomeFromTag(StringTag tag) { return Optional.empty(); }
    }

    private static final class FakeWorldReader implements WorldReader {
        private final Map<Position, Block> blocks = new HashMap<>();
        private final Map<Position, Biome> biomes = new HashMap<>();
        private final Map<Position, ChunkReader> chunks = new HashMap<>();

        void putBlock(int x, int y, int z, Block block) { blocks.put(new Position(x, y, z), block); }

        void putBiome(int x, int y, int z, String name) { biomes.put(new Position(x, y, z), () -> ID.of(name)); }

        void fillChunkBiome(int y, String name) {
            for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
                for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                    putBiome(x, y, z, name);
                }
            }
        }

        void putChunk(ChunkReader chunk) { chunks.put(new Position(chunk.getX(), 0, chunk.getZ()), chunk); }

        @Override
        public Optional<Block> blockAt(int x, int y, int z) { return Optional.of(blocks.getOrDefault(new Position(x, y, z), AIR)); }

        @Override
        public Optional<Biome> biomeAt(int x, int y, int z) { return Optional.ofNullable(biomes.get(new Position(x, y, z))); }

        @Override
        public Optional<ChunkReader> readChunk(int x, int z) { return Optional.ofNullable(chunks.get(new Position(x, 0, z))); }

    }

    private static final class FakeChunkData implements ChunkData {
        private final int minimumY;
        private final int maximumY;
        private final int chunkX;
        private final int chunkZ;
        private final Block defaultBlock;
        private final Map<Position, Block> blocks = new HashMap<>();

        private FakeChunkData(int minimumY, int maximumY, int chunkX, int chunkZ, Block defaultBlock) {
            this.minimumY = minimumY;
            this.maximumY = maximumY;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.defaultBlock = defaultBlock;
        }

        @Override
        public int getMaxHeight() { return maximumY; }

        @Override
        public int getMinHeight() { return minimumY; }

        @Override
        public int getChunkX() { return chunkX; }

        @Override
        public int getChunkZ() { return chunkZ; }

        @Override
        public Block getBlock(int x, int y, int z) { return blocks.getOrDefault(new Position(x, y, z), defaultBlock); }

        @Override
        public Biome getBiome(int x, int y, int z) { return () -> ID.of("plains"); }

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Block block) {
            for (int x = xMin; x < xMax; x++) {
                for (int y = yMin; y < yMax; y++) {
                    for (int z = zMin; z < zMax; z++) {
                        setBlock(x, y, z, block);
                    }
                }
            }
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) { blocks.put(new Position(x, y, z), block); }

        @Override
        public void setBiome(int x, int y, int z, Biome biome) {}

        @Override
        public void addEntity(EntityView entity) {
            throw new UnsupportedOperationException("Fake chunk data cannot add entities");
        }
    }
}
