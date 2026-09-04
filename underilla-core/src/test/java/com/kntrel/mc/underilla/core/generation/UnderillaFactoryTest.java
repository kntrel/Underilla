package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
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
    private static final GenerationConfig CONFIG = new TestGenerationConfig(0, 4).maximumCaveY(0);

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
                UnderillaFactory.absolute(EMPTY_WORLD).config(CONFIG).blocks(BLOCKS).build().flags());
        assertEquals(new GenerationFlags(true, true, true, true, true, true),
                UnderillaFactory.surface(EMPTY_WORLD).underground(EMPTY_WORLD).config(CONFIG).blocks(BLOCKS).build().flags());
        assertEquals(new GenerationFlags(false, true, true, true, true, true),
                UnderillaFactory.none(EMPTY_WORLD).config(CONFIG).blocks(BLOCKS).build().flags());
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

        WorldGenerationPlan underground = UnderillaFactory.absolute(referenceWorld)
                .config(CONFIG)
                .blocks(BLOCKS)
                .noodleCaves(NoodleCavesPolicy.underground())
                .build();
        TestChunkGrid undergroundTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        underground.afterSurface().patch(undergroundTarget);
        assertSame(GENERATED, undergroundTarget.getBlock(0, 1, 0));
        underground.afterCarvers().patch(undergroundTarget);
        assertSame(REFERENCE, undergroundTarget.getBlock(0, 1, 0));

        WorldGenerationPlan protectedSurface = UnderillaFactory.absolute(referenceWorld)
                .config(CONFIG)
                .blocks(BLOCKS)
                .noodleCaves(NoodleCavesPolicy.surface(biome -> false, false))
                .build();
        TestChunkGrid protectedTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        protectedSurface.afterSurface().patch(protectedTarget);
        assertSame(GENERATED, protectedTarget.getBlock(0, 1, 0));
        protectedSurface.afterCarvers().patch(protectedTarget);
        assertSame(REFERENCE, protectedTarget.getBlock(0, 1, 0));

        WorldGenerationPlan cutSurface = UnderillaFactory.absolute(referenceWorld)
                .config(CONFIG)
                .blocks(BLOCKS)
                .noodleCaves(NoodleCavesPolicy.surface(biome -> true, false))
                .build();
        TestChunkGrid cutTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        cutSurface.afterSurface().patch(cutTarget);
        assertSame(REFERENCE, cutTarget.getBlock(0, 1, 0));

        WorldGenerationPlan restoredLiquids = UnderillaFactory.absolute(referenceWorld)
                .config(CONFIG)
                .blocks(BLOCKS)
                .noodleCaves(NoodleCavesPolicy.surface(biome -> true, true))
                .build();
        TestChunkGrid liquidsTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        restoredLiquids.afterSurface().patch(liquidsTarget);
        assertSame(GENERATED, liquidsTarget.getBlock(1, 1, 0));
        restoredLiquids.afterCarvers().patch(liquidsTarget);
        assertSame(WATER, liquidsTarget.getBlock(1, 1, 0));
    }
}
