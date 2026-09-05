package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jkantrell.nbt.tag.CompoundTag;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.api.HeightMapType;
import com.kntrel.mc.underilla.core.api.WorldInfo;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnderillaFactoryTest {

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock GENERATED = TestBlock.solid("minecraft:generated");
    private static final TestBlock REFERENCE = TestBlock.solid("minecraft:reference");
    private static final TestBlock WATER = TestBlock.liquid("minecraft:water");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");
    private static final TestBlockFactory BLOCKS = new TestBlockFactory(AIR, GENERATED, REFERENCE, WATER);

    private static final WorldReader EMPTY_WORLD = new WorldReader() {
        @Override
        public Optional<com.kntrel.mc.underilla.core.api.Block> blockAt(int x, int y, int z) {
            return Optional.empty();
        }

        @Override
        public Optional<Biome> biomeAt(int x, int y, int z) {
            return Optional.empty();
        }

        @Override
        public Optional<ChunkReader> readChunk(int chunkX, int chunkZ) {
            return Optional.empty();
        }
    };

    @Test
    void strategyEntryPointsBuildAnAdapterSafeBaselinePlan() {
        assertEquals(new GenerationFlags(true, true, true, true, true, true),
                configured(UnderillaFactory.absolute(EMPTY_WORLD)).build().flags());
        assertEquals(new GenerationFlags(true, true, true, true, true, true),
                configured(UnderillaFactory.surface(EMPTY_WORLD)).underground(EMPTY_WORLD).build().flags());
        assertEquals(new GenerationFlags(false, true, true, true, true, true),
                configured(UnderillaFactory.none(EMPTY_WORLD)).build().flags());
    }

    @Test
    void generationFlagsAreConfiguredWithoutAConfigObject() {
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(EMPTY_WORLD))
                .carvers(false)
                .features(false)
                .mobs(false)
                .structures(false)
                .build();

        assertEquals(new GenerationFlags(true, true, false, false, false, false), plan.flags());
    }

    @Test
    void noodleCavesPoliciesHaveTheRequestedPublicShape() {
        assertSame(NoodleCavesPolicy.underground(), NoodleCavesPolicy.underground());

        NoodleCavesPolicy.Surface policy = NoodleCavesPolicy.surface(biome -> true, true);
        assertTrue(policy.predicate().test(() -> "minecraft:plains"));
        assertTrue(policy.restoreLiquids());
    }

    @Test
    void noodleCavesPolicySelectsTheSurfaceAndCarverPhases() {
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS);
        referenceChunk.setBlock(1, 1, 0, WATER);
        TestWorld referenceWorld = new TestWorld().addChunk(referenceChunk);

        WorldGenerationPlan underground = configured(UnderillaFactory.absolute(referenceWorld))
                .noodleCaves(NoodleCavesPolicy.underground())
                .build();
        TestChunkGrid undergroundTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        underground.afterSurface().patch(undergroundTarget);
        assertSame(GENERATED, undergroundTarget.getBlock(0, 1, 0));
        underground.afterCarvers().patch(undergroundTarget);
        assertSame(REFERENCE, undergroundTarget.getBlock(0, 1, 0));

        WorldGenerationPlan protectedSurface = configured(UnderillaFactory.absolute(referenceWorld))
                .noodleCaves(NoodleCavesPolicy.surface(biome -> false, false))
                .build();
        TestChunkGrid protectedTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        protectedSurface.afterSurface().patch(protectedTarget);
        assertSame(GENERATED, protectedTarget.getBlock(0, 1, 0));
        protectedSurface.afterCarvers().patch(protectedTarget);
        assertSame(REFERENCE, protectedTarget.getBlock(0, 1, 0));

        WorldGenerationPlan cutSurface = configured(UnderillaFactory.absolute(referenceWorld))
                .noodleCaves(NoodleCavesPolicy.surface(biome -> true, false))
                .build();
        TestChunkGrid cutTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        cutSurface.afterSurface().patch(cutTarget);
        assertSame(REFERENCE, cutTarget.getBlock(0, 1, 0));

        WorldGenerationPlan restoredLiquids = configured(UnderillaFactory.absolute(referenceWorld))
                .noodleCaves(NoodleCavesPolicy.surface(biome -> true, true))
                .build();
        TestChunkGrid liquidsTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        restoredLiquids.afterSurface().patch(liquidsTarget);
        assertSame(GENERATED, liquidsTarget.getBlock(1, 1, 0));
        restoredLiquids.afterCarvers().patch(liquidsTarget);
        assertSame(WATER, liquidsTarget.getBlock(1, 1, 0));
    }

    @Test
    void surfaceBlockDependenciesAreInjectedAsBehavior() {
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS);
        TestWorld referenceWorld = new TestWorld().addChunk(referenceChunk);
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(referenceWorld))
                .maximumCaveY(2)
                .surfaceBlockTransformer(block -> block == REFERENCE ? WATER : block)
                .keptSurfaceBlocks(block -> block == WATER)
                .build();
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);

        plan.afterCarvers().patch(target);

        assertSame(WATER, target.getBlock(0, 1, 0));
        assertSame(WATER, target.getBlock(0, 3, 0));
    }

    @Test
    void finalPlanUsesTheConfiguredSurfaceBiomePatcher() {
        TestBiome reference = new TestBiome("example:reference");
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        referenceChunk.fillBiomeLayer(4, reference);
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(
                        new TestWorld().addChunk(referenceChunk)))
                .generationArea(0, 0, 8, 8)
                .surfaceBiomeUseTopYOnly(true)
                .build();
        MutableBiomeData inside = new MutableBiomeData(PLAINS, 0, 1, 0);
        MutableBiomeData outside = new MutableBiomeData(PLAINS, 8, 1, 0);

        assertTrue(plan.biomePatch().patch(inside));
        assertSame(reference, inside.get());
        assertFalse(plan.biomePatch().patch(outside));
        assertSame(PLAINS, outside.get());
    }

    @Test
    void finalPlanUsesTheReferenceWorldAltimeter() {
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        referenceChunk.setBlock(0, 2, 0, REFERENCE);
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(
                new TestWorld().addChunk(referenceChunk))).build();

        assertEquals(3, plan.altimeter().heightAt(
                new TestWorldInfo(0, 4), 0, 0, HeightMapType.WORLD_SURFACE));
    }

    @Test
    void finalPlanCopiesReferenceEntitiesAfterFeatures() {
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:armor_stand");
        referenceChunk.addEntity(new EntityView(tag, 3955));
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(
                new TestWorld().addChunk(referenceChunk))).build();
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);

        assertTrue(plan.tryAfterFeatures(target));

        assertEquals(1, target.getEntities().size());
        assertEquals("minecraft:armor_stand", target.getEntities().getFirst().tag().getString("id"));
    }

    @Test
    void finalPlanCoverageRequiresConfiguredBoundsAndAReferenceChunk() {
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        TestChunkGrid outOfBoundsChunk = new TestChunkGrid(2, 0, 0, 5, AIR, PLAINS);
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(
                        new TestWorld().addChunk(referenceChunk).addChunk(outOfBoundsChunk)))
                .generationArea(0, 0, 32, 32)
                .build();

        assertTrue(plan.coverage().covers(0, 0));
        assertFalse(plan.coverage().covers(1, 0));
        assertFalse(plan.coverage().covers(2, 0));
    }

    private static UnderillaFactory.Builder configured(UnderillaFactory.Builder builder) {
        return builder.verticalRange(0, 4).maximumCaveY(0).blocks(BLOCKS);
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

    private record TestWorldInfo(int minimumY, int maximumY) implements WorldInfo {
        @Override
        public long getSeed() { return 0; }

        @Override
        public int getMaxHeight() { return maximumY; }

        @Override
        public int getMinHeight() { return minimumY; }
    }
}
