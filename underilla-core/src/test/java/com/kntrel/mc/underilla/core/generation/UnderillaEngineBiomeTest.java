package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.UnderillaEngine;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import org.junit.jupiter.api.Test;

class UnderillaEngineBiomeTest {

    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");
    private static final TestBiome DEEP_DARK = new TestBiome("minecraft:deep_dark");
    private static final TestBiome REFERENCE = new TestBiome("example:reference");
    @Test
    void biomeDataDerivesChunkAndBiomeCoordinatesWithFloorDivision() {
        BiomeData data = new TestBiomeData(PLAINS, -1, -1, -17);

        assertEquals(-1, data.getChunkX());
        assertEquals(-2, data.getChunkZ());
        assertEquals(-1, data.getBiomeX());
        assertEquals(-1, data.getBiomeY());
        assertEquals(-5, data.getBiomeZ());
    }

    @Test
    void unavailableReferenceReturnsFalseWithoutChangingTheBiome() {
        UnderillaEngine engine = engine(new TestWorld(), new TestGenerationConfig(-64, 320), new AbsoluteBoundary(32));
        BiomeData data = new TestBiomeData(PLAINS, 0, 10, 0);

        assertFalse(engine.tryPatchBiome(data));
        assertSame(PLAINS, data.get());
    }

    @Test
    void positionOutsideConfiguredAreaReturnsFalseWithoutChangingTheBiome() {
        TestGenerationConfig config = new TestGenerationConfig(-64, 320).generationArea(0, 0, 8, 8);
        UnderillaEngine engine = engine(referenceWorld(REFERENCE), config, new AbsoluteBoundary(32));
        BiomeData data = new TestBiomeData(PLAINS, 8, 10, 0);

        assertFalse(engine.tryPatchBiome(data));
        assertSame(PLAINS, data.get());
    }

    @Test
    void referenceBiomeReplacesTheCurrentBiome() {
        UnderillaEngine engine = engine(referenceWorld(REFERENCE), new TestGenerationConfig(-64, 320), new AbsoluteBoundary(32));
        BiomeData data = new TestBiomeData(PLAINS, 0, 10, 0);

        assertTrue(engine.tryPatchBiome(data));
        assertSame(REFERENCE, data.get());
    }

    @Test
    void configuredBiomeIsPreservedBelowTheSurface() {
        TestGenerationConfig config = new TestGenerationConfig(-64, 320)
                .preserveGeneratedBiome(DEEP_DARK.getName())
                .preserveGeneratedBiomesOnlyUnderSurface();
        UnderillaEngine engine = engine(referenceWorld(REFERENCE), config, new AbsoluteBoundary(32));
        BiomeData data = new TestBiomeData(DEEP_DARK, 0, 10, 0);

        assertTrue(engine.tryPatchBiome(data));
        assertSame(DEEP_DARK, data.get());
    }

    @Test
    void configuredBiomeIsReplacedAboveTheSurface() {
        TestGenerationConfig config = new TestGenerationConfig(-64, 320)
                .preserveGeneratedBiome(DEEP_DARK.getName())
                .preserveGeneratedBiomesOnlyUnderSurface();
        UnderillaEngine engine = engine(referenceWorld(REFERENCE), config, new AbsoluteBoundary(32));
        BiomeData data = new TestBiomeData(DEEP_DARK, 0, 33, 0);

        assertTrue(engine.tryPatchBiome(data));
        assertSame(REFERENCE, data.get());
    }

    private static UnderillaEngine engine(TestWorld referenceWorld, TestGenerationConfig config, Boundary boundary) {
        TestBlockFactory blocks = new TestBlockFactory(TestBlock.air("minecraft:air"));
        GenerationContext context = new GenerationContext(config, blocks);
        PatchingPlan plan = new PatchingPlan(chunk -> {}, chunk -> {}, boundary, true);
        return new UnderillaEngine(referenceWorld, plan, context, new Instrumenter(_ -> {}));
    }

    private static TestWorld referenceWorld(TestBiome biome) {
        TestBlock air = TestBlock.air("minecraft:air");
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, -64, 321, air, biome);
        return new TestWorld().addChunk(referenceChunk);
    }

    private static final class TestBiomeData implements BiomeData {
        private Biome biome;
        private final int x;
        private final int y;
        private final int z;

        private TestBiomeData(Biome biome, int x, int y, int z) {
            this.biome = biome;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Biome get() { return biome; }

        @Override
        public void set(Biome biome) { this.biome = biome; }

        @Override
        public int getX() { return x; }

        @Override
        public int getY() { return y; }

        @Override
        public int getZ() { return z; }
    }
}
