package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.patch.Patcher;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class UnderillaFactoryBiomePatcherTest {

    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");
    private static final TestBiome DEEP_DARK = new TestBiome("minecraft:deep_dark");
    private static final TestBiome REFERENCE = new TestBiome("example:reference");
    private static final TestBiome SURFACE_ONLY = new TestBiome("example:surface_only");
    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlockFactory BLOCKS = new TestBlockFactory(AIR);

    @Test
    void unavailableReferenceLeavesGeneratedBiomesUnchanged() {
        MutableBiomeData target = new MutableBiomeData(PLAINS, 0, 0, 0);

        patcher(new TestWorld(), 0, 4, 4, _ -> {}).patch(target);

        assertSame(PLAINS, target.get());
    }

    @Test
    void positionsOutsideTheGenerationAreaRemainUnchanged() {
        MutableBiomeData inside = new MutableBiomeData(PLAINS, 4, 0, 0);
        MutableBiomeData outside = new MutableBiomeData(PLAINS, 8, 0, 0);
        Patcher<BiomeData> patcher = patcher(
                referenceWorld(REFERENCE, 0, 4),
                0,
                4,
                4,
                builder -> builder.generationArea(0, 0, 8, 8)
        );

        patcher.patch(inside);
        patcher.patch(outside);

        assertSame(REFERENCE, inside.get());
        assertSame(PLAINS, outside.get());
    }

    @Test
    void referenceBiomesReplaceGeneratedBiomes() {
        MutableBiomeData target = new MutableBiomeData(PLAINS, 0, 0, 0);

        patcher(referenceWorld(REFERENCE, 0, 4), 0, 4, 4, _ -> {}).patch(target);

        assertSame(REFERENCE, target.get());
    }

    @Test
    void selectedGeneratedBiomesArePreservedOnlyOutsideTheMask() {
        MutableBiomeData outsideMask = new MutableBiomeData(DEEP_DARK, 0, 32, 0);
        MutableBiomeData intersectingMask = new MutableBiomeData(DEEP_DARK, 0, 36, 0);
        Patcher<BiomeData> patcher = patcher(
                referenceWorld(REFERENCE, 32, 40),
                32,
                40,
                32,
                builder -> builder
                        .preservedGeneratedBiomes(DEEP_DARK.id()::equals)
                        .preserveGeneratedBiomesOnlyUnderSurface(true)
        );

        patcher.patch(outsideMask);
        patcher.patch(intersectingMask);

        assertSame(DEEP_DARK, outsideMask.get());
        assertSame(REFERENCE, intersectingMask.get());
    }

    @Test
    void surfaceOnlyReferenceBiomesOverrideGeneratedBiomePreservation() {
        MutableBiomeData target = new MutableBiomeData(DEEP_DARK, 0, 0, 0);
        Patcher<BiomeData> patcher = patcher(
                referenceWorld(SURFACE_ONLY, 0, 4),
                0,
                4,
                4,
                builder -> builder
                        .surfaceOnlyBiomes(SURFACE_ONLY.id()::equals)
                        .preservedGeneratedBiomes(DEEP_DARK.id()::equals)
        );

        patcher.patch(target);

        assertSame(SURFACE_ONLY, target.get());
    }

    @Test
    void topYPolicySamplesOneReferenceLayerForEveryBiome() {
        TestChunkGrid referenceChunk = chunk(0, 5, PLAINS);
        referenceChunk.fillBiomeLayer(4, REFERENCE);
        MutableBiomeData target = new MutableBiomeData(PLAINS, 0, 0, 0);
        Patcher<BiomeData> patcher = patcher(
                new TestWorld().addChunk(referenceChunk),
                0,
                4,
                4,
                builder -> builder.surfaceBiomeUseTopYOnly(true)
        );

        patcher.patch(target);

        assertSame(REFERENCE, target.get());
    }

    private static Patcher<BiomeData> patcher(
            TestWorld referenceWorld,
            int minimumY,
            int maximumY,
            int maximumCaveY,
            Consumer<UnderillaFactory.Builder> configure
    ) {
        UnderillaFactory.Builder builder = UnderillaFactory.absolute(referenceWorld)
                .verticalRange(minimumY, maximumY)
                .maximumCaveY(maximumCaveY)
                .blocks(BLOCKS);
        configure.accept(builder);
        return builder.build().biomePatch();
    }

    private static TestWorld referenceWorld(TestBiome biome, int minimumY, int maximumY) {
        return new TestWorld().addChunk(chunk(minimumY, maximumY, biome));
    }

    private static TestChunkGrid chunk(int minimumY, int maximumY, TestBiome biome) {
        return new TestChunkGrid(0, 0, minimumY, maximumY, AIR, biome);
    }

    private static final class MutableBiomeData implements BiomeData {
        private Biome biome;
        private final int x;
        private final int y;
        private final int z;

        private MutableBiomeData(Biome biome, int x, int y, int z) {
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
