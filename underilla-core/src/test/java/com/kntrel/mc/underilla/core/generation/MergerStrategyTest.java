package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jkantrell.nbt.tag.CompoundTag;
import com.jkantrell.nbt.tag.StringTag;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.LocatedBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class PatcherStrategyTest {

    private static final TestBlock AIR = new TestBlock("minecraft:air", false, false, true);
    private static final TestBlock WATER = new TestBlock("minecraft:water", false, true, false);
    private static final TestBlock STONE = new TestBlock("minecraft:stone", true, false, false);
    private static final TestBlock LEAVES = new TestBlock("minecraft:oak_leaves", true, false, false);
    private static final TestBlock REFERENCE = new TestBlock("minecraft:reference", true, false, false);
    private static final TestBlock GENERATED = new TestBlock("minecraft:generated", true, false, false);
    @Test
    void boundaryPredicatesHaveExplicitEqualitySemantics() {
        Boundary boundary = new AbsoluteBoundary(10);

        assertTrue(boundary.isAbove(0, 11, 0));
        assertFalse(boundary.isAbove(0, 10, 0));
        assertTrue(boundary.isAboveEquals(0, 10, 0));
        assertTrue(boundary.isBelow(0, 9, 0));
        assertFalse(boundary.isBelow(0, 10, 0));
        assertTrue(boundary.isBelowEquals(0, 10, 0));
    }

    @Test
    void absolutePatcherUsesOneFixedBoundary() {
        TestConfig config = new TestConfig();
        config.minimumY = 0;
        config.maximumY = 8;
        config.maximumCaveY = 2;
        GenerationContext context = context(config);
        FakeChunkReader referenceChunk = FakeChunkReader.filled(0, 0, 5, REFERENCE);
        FakeWorldReader referenceWorld = new FakeWorldReader();
        referenceWorld.putChunk(referenceChunk);
        Boundary boundary = new AbsoluteBoundary(config.maximumCaveY, config.minimumY, config.maximumY);
        ChunkPatcher patcher = surfacePatcher(referenceWorld, boundary, context);
        FakeChunkData destination = new FakeChunkData(0, 8, 0, 0, GENERATED);

        patcher.patch(destination);

        assertSame(GENERATED, destination.getBlock(0, 2, 0));
        assertSame(REFERENCE, destination.getBlock(0, 3, 0));
        assertSame(REFERENCE, destination.getBlock(0, 4, 0));
        assertSame(AIR, destination.getBlock(0, 5, 0));
        assertTrue(boundary.isBelowEquals(100, 2, -100));
        assertFalse(boundary.isBelowEquals(100, 3, -100));
    }

    @Test
    void surfaceBoundaryCalculatesAndOwnsThePerColumnBoundary() {
        TestConfig config = new TestConfig();
        config.minimumY = 0;
        config.maximumY = 8;
        config.maximumCaveY = 6;
        config.depth = 2;
        config.adaptiveMaximumDepth = 2;
        config.ignoredSurfaceBlocks = Set.of(LEAVES.getName());
        FakeWorldReader world = new FakeWorldReader();
        world.putBlock(0, 4, 0, STONE);
        world.putBlock(0, 5, 0, LEAVES);
        world.putBiome(0, config.maximumY, 0, "minecraft:plains");

        Boundary boundary = heightBoundary(world, config);

        assertTrue(boundary.isBelowEquals(0, 2, 0));
        assertFalse(boundary.isBelowEquals(0, 3, 0));
    }

    @Test
    void surfaceBoundaryPreservesTheWholeReferenceColumnForConfiguredBiomes() {
        TestConfig config = new TestConfig();
        config.minimumY = -64;
        config.maximumY = 320;
        config.maximumCaveY = 200;
        config.preservedBiomes = Set.of("example:preserved");
        FakeWorldReader world = new FakeWorldReader();
        world.putBiome(12, config.maximumY, -4, "example:preserved");

        Boundary boundary = heightBoundary(world, config);

        assertTrue(boundary.isBelowEquals(12, -64, -4));
        assertFalse(boundary.isBelowEquals(12, -63, -4));
    }

    @Test
    void referenceOnlyPlanDisablesGeneratedNoise() {
        TestConfig config = new TestConfig();
        config.minimumY = -64;
        GenerationContext context = context(config);
        Boundary boundary = new AbsoluteBoundary(config.minimumY);
        FakeWorldReader referenceWorld = new FakeWorldReader();
        ChunkPatcher terrainPatcher = surfacePatcher(referenceWorld, boundary, context);
        PatchingPlan plan = new PatchingPlan(terrainPatcher, chunk -> {}, boundary, false);

        assertFalse(plan.generateNoise());
        assertTrue(boundary.isBelowEquals(0, -64, 0));
        assertFalse(boundary.isBelowEquals(0, -63, 0));
    }

    @Test
    void liquidPatcherOnlyRestoresLiquidsAboveTheBoundary() {
        TestConfig config = new TestConfig();
        config.minimumY = 0;
        config.maximumY = 8;
        config.maximumCaveY = 2;
        GenerationContext context = context(config);
        Boundary boundary = new AbsoluteBoundary(config.maximumCaveY, config.minimumY, config.maximumY);
        FakeChunkReader surfaceChunk = FakeChunkReader.filled(0, 0, 5, REFERENCE);
        surfaceChunk.putBlock(0, 2, 0, WATER);
        surfaceChunk.putBlock(0, 3, 0, WATER);
        FakeWorldReader surfaceWorld = new FakeWorldReader();
        surfaceWorld.putChunk(surfaceChunk);
        TestBlock boundaryBlock = new TestBlock("minecraft:boundary_target", true, false, false);
        TestBlock aboveBlock = new TestBlock("minecraft:above_target", true, false, false);
        FakeChunkData destination = new FakeChunkData(0, 8, 0, 0, GENERATED);
        destination.setBlock(0, 2, 0, boundaryBlock);
        destination.setBlock(0, 3, 0, aboveBlock);

        new LiquidPatcher(surfaceWorld, boundary).patch(destination);

        assertFalse(boundaryBlock.isWaterlogged());
        assertTrue(aboveBlock.isWaterlogged());
    }

    private static GenerationContext context(TestConfig config) {
        BlockFactory blockFactory = new BlockFactory() {
            @Override
            public Block air() { return AIR; }

            @Override
            public Block create(String name) { return new TestBlock(name, true, false, false); }
        };
        return new GenerationContext(config, blockFactory);
    }

    private static ChunkPatcher surfacePatcher(
            WorldReader surfaceWorld,
            Boundary boundary,
            GenerationContext context
    ) {
        GenerationConfig config = context.config();
        BlockFactory blocks = context.blocks();
        return new SurfacePatcher(
                surfaceWorld,
                boundary,
                config.generationAreaMinY(),
                blocks::air,
                block -> config.shouldKeepSurfaceBlockInCaves(block.getName()),
                block -> {
                    String replacement = config.surfaceBlockReplacement(block.getName());
                    return replacement == null ? block : blocks.create(replacement);
                });
    }

    private static Boundary heightBoundary(WorldReader surfaceWorld, TestConfig config) {
        return new HeightBoundary(surfaceWorld, AIR,
                config.generationAreaMinY(), config.generationAreaMaxY(), config.maxHeightOfCaves(),
                config.mergeDepth(), config.adaptiveMaxMergeDepth(), config.adaptiveMinHiddenBlocksMergeDepth(),
                config::isSurfaceWorldOnlyBiome, config::isIgnoredForSurfaceCalculation);
    }

    private static final class TestConfig implements GenerationConfig {
        private int minimumY = -64;
        private int maximumY = 320;
        private int maximumCaveY = 320;
        private int depth;
        private int adaptiveMaximumDepth;
        private int adaptiveMinimumHiddenDepth;
        private Set<String> preservedBiomes = Set.of();
        private Set<String> ignoredSurfaceBlocks = Set.of();

        @Override
        public int cacheSize() { return 1; }

        @Override
        public int generationAreaMinX() { return Integer.MIN_VALUE; }

        @Override
        public int generationAreaMinY() { return minimumY; }

        @Override
        public int generationAreaMinZ() { return Integer.MIN_VALUE; }

        @Override
        public int generationAreaMaxX() { return Integer.MAX_VALUE; }

        @Override
        public int generationAreaMaxY() { return maximumY; }

        @Override
        public int generationAreaMaxZ() { return Integer.MAX_VALUE; }

        @Override
        public int maxHeightOfCaves() { return maximumCaveY; }

        @Override
        public int mergeDepth() { return depth; }

        @Override
        public int adaptiveMaxMergeDepth() { return adaptiveMaximumDepth; }

        @Override
        public int adaptiveMinHiddenBlocksMergeDepth() { return adaptiveMinimumHiddenDepth; }

        @Override
        public boolean carversEnabled() { return true; }

        @Override
        public boolean vanillaPopulationEnabled() { return true; }

        @Override
        public boolean structuresEnabled() { return true; }

        @Override
        public boolean surfaceBiomeUseTopYOnly() { return false; }

        @Override
        public boolean shouldPreserveBiome(String biomeName) { return false; }

        @Override
        public boolean preserveBiomesOnlyUnderSurface() { return false; }

        @Override
        public boolean isSurfaceWorldOnlyBiome(String biomeName) { return preservedBiomes.contains(biomeName); }

        @Override
        public boolean isIgnoredForSurfaceCalculation(String blockName) { return ignoredSurfaceBlocks.contains(blockName); }

        @Override
        public boolean shouldKeepSurfaceBlockInCaves(String blockName) { return false; }

        @Override
        public String surfaceBlockReplacement(String blockName) { return null; }
    }

    private static final class TestBlock implements Block {
        private final String name;
        private final boolean solid;
        private final boolean liquid;
        private final boolean air;
        private boolean waterlogged;

        private TestBlock(String name, boolean solid, boolean liquid, boolean air) {
            this.name = name;
            this.solid = solid;
            this.liquid = liquid;
            this.air = air;
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
        public void waterlog() { waterlogged = true; }

        boolean isWaterlogged() { return waterlogged; }

        @Override
        public String getName() { return name; }

        @Override
        public String getNameSpace() { return name.substring(0, name.indexOf(':')); }
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
        public Optional<Block> blockFromTag(CompoundTag tag) { return Optional.empty(); }

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

        void putBiome(int x, int y, int z, String name) { biomes.put(new Position(x, y, z), () -> name); }

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
        public Biome getBiome(int x, int y, int z) { return () -> "minecraft:plains"; }

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
