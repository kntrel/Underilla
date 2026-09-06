package com.kntrel.mc.underilla.paper.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.paper.profiling.ChunkGenerationProfiler;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.FeatureFlag;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.material.MaterialData;
import org.junit.jupiter.api.Test;

class UnderillaChunkGeneratorTest {

    private static final UUID WORLD_ID = UUID.fromString("96d2f544-3390-4e33-b9fa-19f65c59fcb8");
    private static final WorldInfo WORLD_INFO = new TestWorldInfo();
    private static final Random RANDOM = new Random(1);

    @Test
    void mapsPaperGenerationCallbacksToPlanPhases() {
        List<String> phases = new ArrayList<>();
        WorldGenerationPlan plan = WorldGenerationPlan.build()
                .afterNoise(_ -> phases.add("noise"))
                .afterSurface(_ -> phases.add("surface"))
                .afterCarvers(_ -> phases.add("carvers"))
                .altimeter((world, x, z, heightMap) -> 42)
                .noise(false)
                .surface(false)
                .features(false)
                .mobs(false)
                .structures(false)
                .done();
        UnderillaChunkGenerator generator = generator(plan, null);
        ChunkGenerator.ChunkData chunk = new TestChunkData();

        generator.generateNoise(WORLD_INFO, RANDOM, 2, -3, chunk);
        generator.generateSurface(WORLD_INFO, RANDOM, 2, -3, chunk);
        generator.generateCaves(WORLD_INFO, RANDOM, 2, -3, chunk);

        assertEquals(List.of("noise", "surface", "carvers"), phases);
        assertEquals(42, generator.getBaseHeight(WORLD_INFO, RANDOM, 32, -48, HeightMap.WORLD_SURFACE));
        assertFalse(generator.shouldGenerateNoise(WORLD_INFO, RANDOM, 2, -3));
        assertFalse(generator.shouldGenerateSurface(WORLD_INFO, RANDOM, 2, -3));
        assertFalse(generator.shouldGenerateDecorations(WORLD_INFO, RANDOM, 2, -3));
        assertFalse(generator.shouldGenerateMobs(WORLD_INFO, RANDOM, 2, -3));
        assertFalse(generator.shouldGenerateStructures(WORLD_INFO, RANDOM, 2, -3));
    }

    @Test
    void delegatesUncoveredChunksToTheFallbackGenerator() {
        List<String> phases = new ArrayList<>();
        WorldGenerationPlan plan = WorldGenerationPlan.build()
                .coverage((chunkX, chunkZ) -> false)
                .afterNoise(_ -> phases.add("plan-noise"))
                .afterSurface(_ -> phases.add("plan-surface"))
                .afterCarvers(_ -> phases.add("plan-carvers"))
                .done();
        RecordingFallback fallback = new RecordingFallback(phases);
        UnderillaChunkGenerator generator = generator(plan, fallback);
        ChunkGenerator.ChunkData chunk = new TestChunkData();

        generator.generateNoise(WORLD_INFO, RANDOM, 2, -3, chunk);
        generator.generateSurface(WORLD_INFO, RANDOM, 2, -3, chunk);
        generator.generateCaves(WORLD_INFO, RANDOM, 2, -3, chunk);

        assertEquals(List.of("fallback-noise", "fallback-surface", "fallback-carvers"), phases);
        assertFalse(generator.shouldGenerateNoise(WORLD_INFO, RANDOM, 2, -3));
        assertFalse(generator.shouldGenerateSurface(WORLD_INFO, RANDOM, 2, -3));
        assertTrue(generator.shouldGenerateCaves(WORLD_INFO, RANDOM, 2, -3));
        assertFalse(generator.shouldGenerateDecorations(WORLD_INFO, RANDOM, 2, -3));
        assertTrue(generator.shouldGenerateMobs(WORLD_INFO, RANDOM, 2, -3));
        assertFalse(generator.shouldGenerateStructures(WORLD_INFO, RANDOM, 2, -3));
    }

    private static UnderillaChunkGenerator generator(
            WorldGenerationPlan plan,
            ChunkGenerator fallback
    ) {
        ChunkGenerationProfiler profiler = new ChunkGenerationProfiler(new Instrumenter(_ -> {}));
        return new UnderillaChunkGenerator(plan, fallback, profiler, WORLD_INFO.getName());
    }

    private static final class RecordingFallback extends ChunkGenerator {

        private final List<String> phases;

        private RecordingFallback(List<String> phases) { this.phases = phases; }

        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            phases.add("fallback-noise");
        }

        @Override
        public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            phases.add("fallback-surface");
        }

        @Override
        public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            phases.add("fallback-carvers");
        }

        @Override
        public boolean shouldGenerateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return false; }

        @Override
        public boolean shouldGenerateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return false; }

        @Override
        public boolean shouldGenerateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return true; }

        @Override
        public boolean shouldGenerateDecorations(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return false; }

        @Override
        public boolean shouldGenerateMobs(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return true; }

        @Override
        public boolean shouldGenerateStructures(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return false; }
    }

    private static final class TestWorldInfo implements WorldInfo {

        @Override
        public String getName() { return "world"; }

        @Override
        public UUID getUID() { return WORLD_ID; }

        @Override
        public World.Environment getEnvironment() { return World.Environment.NORMAL; }

        @Override
        public long getSeed() { return 1; }

        @Override
        public int getMinHeight() { return -64; }

        @Override
        public int getMaxHeight() { return 320; }

        @Override
        public BiomeProvider vanillaBiomeProvider() { return null; }

        @Override
        public Set<FeatureFlag> getFeatureFlags() { return Set.of(); }
    }

    @SuppressWarnings("deprecation")
    private static final class TestChunkData implements ChunkGenerator.ChunkData {

        @Override
        public int getMinHeight() { return -64; }

        @Override
        public int getMaxHeight() { return 320; }

        @Override
        public Biome getBiome(int x, int y, int z) { return null; }

        @Override
        public void setBlock(int x, int y, int z, Material material) {}

        @Override
        public void setBlock(int x, int y, int z, MaterialData material) {}

        @Override
        public void setBlock(int x, int y, int z, BlockData blockData) {}

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material) {}

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material) {}

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData) {}

        @Override
        public Material getType(int x, int y, int z) { return Material.AIR; }

        @Override
        public MaterialData getTypeAndData(int x, int y, int z) { return new MaterialData(Material.AIR); }

        @Override
        public BlockData getBlockData(int x, int y, int z) { return Material.AIR.createBlockData(); }

        @Override
        public byte getData(int x, int y, int z) { return 0; }

        @Override
        public int getHeight(HeightMap heightMap, int x, int z) { return 0; }
    }
}
