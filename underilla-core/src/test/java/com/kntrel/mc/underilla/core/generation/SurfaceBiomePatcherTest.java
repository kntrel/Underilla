package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import org.junit.jupiter.api.Test;

class SurfaceBiomePatcherTest {

    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");
    private static final TestBiome DEEP_DARK = new TestBiome("minecraft:deep_dark");
    private static final TestBiome REFERENCE = new TestBiome("example:reference");
    private static final TestBiome SURFACE_ONLY = new TestBiome("example:surface_only");

    @Test
    void unavailableReferenceDoesNotHandleOrChangeTheBiome() {
        SurfaceBiomePatcher patcher = patcher(new TestWorld(), new AbsoluteBoundary(32));
        TestBiomeData data = new TestBiomeData(PLAINS, 0, 10, 0);

        assertFalse(patcher.patch(data));
        assertSame(PLAINS, data.get());
    }

    @Test
    void positionOutsideGenerationAreaDoesNotHandleOrChangeTheBiome() {
        SurfaceBiomePatcher patcher = new SurfaceBiomePatcher(
                referenceWorld(REFERENCE),
                new AbsoluteBoundary(32),
                new GenerationArea(0, 0, 8, 8),
                32,
                false,
                _ -> false,
                _ -> false,
                false
        );
        TestBiomeData data = new TestBiomeData(PLAINS, 8, 10, 0);

        assertFalse(patcher.patch(data));
        assertSame(PLAINS, data.get());
    }

    @Test
    void referenceBiomeReplacesTheGeneratedBiome() {
        SurfaceBiomePatcher patcher = patcher(referenceWorld(REFERENCE), new AbsoluteBoundary(32));
        TestBiomeData data = new TestBiomeData(PLAINS, 0, 10, 0);

        assertTrue(patcher.patch(data));
        assertSame(REFERENCE, data.get());
    }

    @Test
    void selectedGeneratedBiomeIsPreservedOnlyBelowTheWholeSurfaceCell() {
        SurfaceBiomePatcher patcher = new SurfaceBiomePatcher(
                referenceWorld(REFERENCE),
                new AbsoluteBoundary(32),
                GenerationArea.everywhere(),
                64,
                false,
                _ -> false,
                DEEP_DARK.getName()::equals,
                true
        );
        TestBiomeData below = new TestBiomeData(DEEP_DARK, 0, 32, 0);
        TestBiomeData above = new TestBiomeData(DEEP_DARK, 0, 33, 0);

        assertTrue(patcher.patch(below));
        assertSame(DEEP_DARK, below.get());
        assertTrue(patcher.patch(above));
        assertSame(REFERENCE, above.get());
    }

    @Test
    void surfaceOnlyReferenceBiomeOverridesGeneratedBiomePreservation() {
        SurfaceBiomePatcher patcher = new SurfaceBiomePatcher(
                referenceWorld(SURFACE_ONLY),
                new AbsoluteBoundary(32),
                GenerationArea.everywhere(),
                64,
                false,
                SURFACE_ONLY.getName()::equals,
                DEEP_DARK.getName()::equals,
                false
        );
        TestBiomeData data = new TestBiomeData(DEEP_DARK, 0, 10, 0);

        assertTrue(patcher.patch(data));
        assertSame(SURFACE_ONLY, data.get());
    }

    @Test
    void topYPolicySamplesOneReferenceLayerForEveryBiome() {
        TestBlock air = TestBlock.air("minecraft:air");
        TestChunkGrid chunk = new TestChunkGrid(0, 0, 0, 5, air, PLAINS);
        chunk.fillBiomeLayer(4, REFERENCE);
        SurfaceBiomePatcher patcher = new SurfaceBiomePatcher(
                new TestWorld().addChunk(chunk),
                new AbsoluteBoundary(32),
                GenerationArea.everywhere(),
                4,
                true,
                _ -> false,
                _ -> false,
                false
        );
        TestBiomeData data = new TestBiomeData(PLAINS, 0, 1, 0);

        assertTrue(patcher.patch(data));
        assertSame(REFERENCE, data.get());
    }

    private static SurfaceBiomePatcher patcher(TestWorld referenceWorld, Boundary boundary) {
        return new SurfaceBiomePatcher(
                referenceWorld,
                boundary,
                GenerationArea.everywhere(),
                64,
                false,
                _ -> false,
                _ -> false,
                false
        );
    }

    private static TestWorld referenceWorld(TestBiome biome) {
        TestBlock air = TestBlock.air("minecraft:air");
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, -64, 65, air, biome);
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
