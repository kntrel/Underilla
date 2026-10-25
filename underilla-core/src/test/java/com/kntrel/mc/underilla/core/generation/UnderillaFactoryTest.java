package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jkantrell.nbt.tag.CompoundTag;
import com.kntrel.mc.underilla.core.api.*;
import com.kntrel.mc.underilla.core.api.context.BiomeContext;
import com.kntrel.mc.underilla.core.api.context.ChunkView;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.inspector.InspectionRegion;
import com.kntrel.mc.underilla.core.inspector.WorldSlice;
import com.kntrel.mc.underilla.core.inspector.WorldSliceRenderer;
import com.kntrel.mc.underilla.core.patch.ChunkBlock;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.patch.VerticalBoundChunkPatcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.reference.ReferenceWorldPatchers;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Collection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnderillaFactoryTest {

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock GENERATED = TestBlock.solid("minecraft:generated");
    private static final TestBlock REFERENCE = TestBlock.solid("minecraft:reference");
    private static final TestBlock WATER = TestBlock.liquid("minecraft:water");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");
    private static final TestBlockFactory BLOCKS = new TestBlockFactory(AIR, GENERATED, REFERENCE, WATER);

    @Test
    void inspectionWritesReferenceBeforeGeneration(@TempDir Path output) throws IOException {
        TestWorld reference = new TestWorld(0, 4, AIR, PLAINS);
        reference.setBlock(0, 0, 0, REFERENCE);

        InspectionRegion region = InspectionRegion.zSlice(0, 0, 1, 0, 4);
        UnderillaFactory.none(reference)
                .verticalRange(0, 4)
                .blocks(BLOCKS)
                .generationArea(0, 0, 16, 16)
                .inspect(region, output)
                .build();

        Path image = output.resolve("0.0 reference.png");
        assertTrue(Files.exists(image));
        var rendered = javax.imageio.ImageIO.read(image.toFile());
        var expected = WorldSliceRenderer.render(WorldSlice.from(reference, region), 2);
        assertEquals(expected.getWidth(), rendered.getWidth());
        assertEquals(expected.getRGB(0, 7), rendered.getRGB(0, 7));
    }

    @Test
    void inspectionAppliesBiomeOverlayToRenderedReference(@TempDir Path output) throws IOException {
        TestWorld reference = new TestWorld(0, 4, AIR, PLAINS);
        reference.setBlock(0, 0, 0, REFERENCE);
        InspectionRegion region = InspectionRegion.zSlice(0, 0, 1, 0, 4);

        UnderillaFactory.none(reference)
                .verticalRange(0, 4)
                .blocks(BLOCKS)
                .generationArea(0, 0, 16, 16)
                .inspect(region, output, "overlay-test", true)
                .build();

        var rendered = javax.imageio.ImageIO.read(output.resolve("0.0 reference.png").toFile());
        var expected = WorldSliceRenderer.render(WorldSlice.from(reference, region), 2, true);
        assertArrayEquals(expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
                rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth()));
    }

    @Test
    void inspectionShrinksBothEndsOfZSliceToGenerationArea(@TempDir Path output) throws IOException {
        TestWorld reference = new TestWorld(0, 4, AIR, PLAINS);
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            reference.addChunk(new TestChunkGrid(chunkX, 0, 0, 4, AIR, PLAINS));
        }
        reference.setBlock(-16, 0, 0, REFERENCE);
        InspectionRegion requested = InspectionRegion.zSlice(0, -2, 5, 0, 4);
        InspectionRegion fitted = InspectionRegion.zSlice(0, -1, 3, 0, 4);

        UnderillaFactory.none(reference)
                .verticalRange(0, 4)
                .blocks(BLOCKS)
                .generationArea(-16, 0, 32, 16)
                .inspect(requested, output)
                .build();

        var rendered = javax.imageio.ImageIO.read(output.resolve("0.0 reference.png").toFile());
        var expected = WorldSliceRenderer.render(WorldSlice.from(reference, fitted), 2);
        assertEquals(expected.getWidth(), rendered.getWidth());
        assertArrayEquals(expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
                rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth()));
    }

    @Test
    void inspectionShrinksBothEndsOfXSliceToGenerationArea(@TempDir Path output) throws IOException {
        TestWorld reference = new TestWorld(0, 4, AIR, PLAINS);
        for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
            reference.addChunk(new TestChunkGrid(0, chunkZ, 0, 4, AIR, PLAINS));
        }
        reference.setBlock(0, 0, -16, REFERENCE);
        InspectionRegion requested = InspectionRegion.xSlice(0, -2, 5, 0, 4);
        InspectionRegion fitted = InspectionRegion.xSlice(0, -1, 3, 0, 4);

        UnderillaFactory.none(reference)
                .verticalRange(0, 4)
                .blocks(BLOCKS)
                .generationArea(0, -16, 16, 32)
                .inspect(requested, output)
                .build();

        var rendered = javax.imageio.ImageIO.read(output.resolve("0.0 reference.png").toFile());
        var expected = WorldSliceRenderer.render(WorldSlice.from(reference, fitted), 2);
        assertEquals(expected.getWidth(), rendered.getWidth());
        assertArrayEquals(expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
                rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth()));
    }

    @Test
    void inspectionShrinksBothYBoundsToGenerationRange(@TempDir Path output) throws IOException {
        TestWorld reference = new TestWorld(0, 4, AIR, PLAINS)
                .addChunk(new TestChunkGrid(0, 0, 0, 4, AIR, PLAINS));
        reference.setBlock(0, 0, 0, REFERENCE);
        reference.setBlock(0, 3, 0, REFERENCE);

        UnderillaFactory.none(reference)
                .verticalRange(0, 4)
                .blocks(BLOCKS)
                .generationArea(0, 0, 16, 16)
                .inspect(InspectionRegion.zSlice(0, 0, 1, -2, 6), output)
                .build();

        var rendered = javax.imageio.ImageIO.read(output.resolve("0.0 reference.png").toFile());
        var expected = WorldSliceRenderer.render(
                WorldSlice.from(reference, InspectionRegion.zSlice(0, 0, 1, 0, 4)), 2);
        assertEquals(expected.getHeight(), rendered.getHeight());
        assertArrayEquals(expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
                rendered.getRGB(0, 0, rendered.getWidth(), rendered.getHeight(), null, 0, rendered.getWidth()));
    }

    @Test
    void inspectionOutsideGenerationYRangeIsSkipped(@TempDir Path output) {
        TestWorld reference = new TestWorld(0, 4, AIR, PLAINS);

        UnderillaFactory.none(reference)
                .verticalRange(0, 4)
                .blocks(BLOCKS)
                .generationArea(0, 0, 16, 16)
                .inspect(InspectionRegion.zSlice(0, 0, 1, 4, 8), output)
                .build();

        assertFalse(Files.exists(output.resolve("0.0 reference.png")));
    }

    @Test
    void inspectionOutsideGenerationAreaIsSkipped(@TempDir Path output) {
        TestWorld reference = new TestWorld(0, 4, AIR, PLAINS)
                .addChunk(new TestChunkGrid(0, 0, 0, 4, AIR, PLAINS));

        WorldGenerationPlan plan = UnderillaFactory.none(reference)
                .verticalRange(0, 4)
                .blocks(BLOCKS)
                .generationArea(0, 0, 16, 16)
                .inspect(InspectionRegion.zSlice(32, 0, 1, 0, 4), output)
                .build();

        assertTrue(plan.coverage().covers(0, 0));
        assertFalse(Files.exists(output.resolve("0.0 reference.png")));
    }

    @Test
    void inspectionBeyondTraversedGenerationBoundsIsSkipped(@TempDir Path output) {
        TestWorld reference = new TestWorld(0, 4, AIR, PLAINS);

        UnderillaFactory.none(reference)
                .verticalRange(0, 4)
                .blocks(BLOCKS)
                .generationArea(0, 0, 16, 16)
                .inspect(InspectionRegion.zSlice(0, 2, 1, 0, 4), output)
                .build();

        assertFalse(Files.exists(output.resolve("0.0 reference.png")));
    }

    private static final WorldReader EMPTY_WORLD = new WorldReader() {
        @Override public String name() { return "test-world"; }
        @Override public com.kntrel.mc.underilla.core.api.ID dimension() { return com.kntrel.mc.underilla.core.api.ID.of("minecraft:overworld"); }
        @Override public int minHeight() { return -64; }
        @Override public int maxHeight() { return 320; }

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
    void configuredVerticalRangeBoundsEveryChunkPhase() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, -2, 5, REFERENCE, PLAINS));
        WorldGenerationPlan plan = UnderillaFactory.absolute(referenceWorld)
                .verticalRange(0, 3)
                .maximumCaveY(0)
                .blocks(BLOCKS)
                .keptSurfaceBlocks(_ -> true)
                .surfaceFill(false)
                .build();
        TestChunkGrid target = new TestChunkGrid(0, 0, -2, 5, GENERATED, PLAINS);

        assertInstanceOf(VerticalBoundChunkPatcher.class, plan.afterNoise());
        assertInstanceOf(VerticalBoundChunkPatcher.class, plan.afterSurface());
        assertInstanceOf(VerticalBoundChunkPatcher.class, plan.afterCarvers());
        assertInstanceOf(VerticalBoundChunkPatcher.class, plan.afterFeatures());
        assertInstanceOf(VerticalBoundChunkPatcher.class, plan.afterLoad());

        plan.afterCarvers().patch(target);

        assertEquals(GENERATED.id(), target.getBlock(0, -2, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(0, -1, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, 2, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(0, 3, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(0, 4, 0).id());
    }

    @Test
    void defaultVerticalRangeLeavesChunkPhasesUnwrapped() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, -64, 320, REFERENCE, PLAINS));

        WorldGenerationPlan plan = UnderillaFactory.absolute(referenceWorld)
                .verticalRange(-64, 320)
                .maximumCaveY(0)
                .blocks(BLOCKS)
                .build();

        assertFalse(plan.afterNoise() instanceof VerticalBoundChunkPatcher);
        assertFalse(plan.afterSurface() instanceof VerticalBoundChunkPatcher);
        assertFalse(plan.afterCarvers() instanceof VerticalBoundChunkPatcher);
        assertFalse(plan.afterFeatures() instanceof VerticalBoundChunkPatcher);
        assertFalse(plan.afterLoad() instanceof VerticalBoundChunkPatcher);
    }

    @Test
    void alternativeReferenceWorldPatcherGatesDirectWritesByMask() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 2, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 2, GENERATED, PLAINS);
        WorldMask firstColumn = (x, _, _) -> x == 0;
        Patcher<ChunkData> patcher = referenceWorldPatcher(
                referenceWorld,
                firstColumn,
                () -> AIR,
                null,
                java.util.List.of()
        );

        patcher.patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, 1, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(1, 1, 0).id());
    }

    @Test
    void alternativeReferenceWorldPatcherTestsTransformedCandidatesForSurvival() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        target.setBlock(1, 0, 0, AIR);
        Patcher<ChunkBlock> transformReference = candidate -> candidate.replace(WATER);
        Patcher<ChunkData> patcher = referenceWorldPatcher(
                referenceWorld,
                (_, _, _) -> false,
                () -> AIR,
                block -> block.id().equals(WATER.id()),
                java.util.List.of(transformReference)
        );

        patcher.patch(target);

        assertEquals(WATER.id(), target.getBlock(0, 0, 0).id());
        assertEquals(AIR.id(), target.getBlock(1, 0, 0).id());
    }

    @Test
    void alternativeReferenceWorldPatcherReadsTransformedCandidatesAtGlobalCoordinates() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(2, -1, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(2, -1, 0, 1, GENERATED, PLAINS);
        Patcher<ChunkData> patcher = referenceWorldPatcher(
                referenceWorld,
                (x, _, z) -> x == 32 && z == -16,
                () -> AIR,
                null,
                java.util.List.of(_ -> {})
        );

        patcher.patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
    }

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
    void surfaceFillIsEnabledByDefaultAndCanBeDisabled() {
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        referenceChunk.setBlock(0, 3, 0, REFERENCE);
        TestWorld referenceWorld = new TestWorld().addChunk(referenceChunk);
        WorldGenerationPlan enabled = UnderillaFactory.absolute(referenceWorld)
                .verticalRange(0, 5)
                .maximumCaveY(3)
                .blocks(BLOCKS)
                .build();
        WorldGenerationPlan disabled = UnderillaFactory.absolute(referenceWorld)
                .verticalRange(0, 5)
                .maximumCaveY(3)
                .blocks(BLOCKS)
                .surfaceFill(false)
                .build();
        TestChunkGrid enabledTarget = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        TestChunkGrid disabledTarget = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        enabledTarget.setBlock(0, 1, 0, GENERATED);
        disabledTarget.setBlock(0, 1, 0, GENERATED);

        enabled.afterCarvers().patch(enabledTarget);
        disabled.afterCarvers().patch(disabledTarget);

        assertEquals(REFERENCE.id(), enabledTarget.getBlock(0, 3, 0).id());
        assertEquals(AIR.id(), disabledTarget.getBlock(0, 3, 0).id());
    }

    @Test
    void configuringASecondReferenceWorldDisablesSurfaceFill() {
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        referenceChunk.setBlock(0, 2, 0, REFERENCE);
        TestWorld referenceWorld = new TestWorld().addChunk(referenceChunk);
        TestWorld undergroundWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 5, GENERATED, PLAINS)
        );
        WorldGenerationPlan plan = UnderillaFactory.absolute(referenceWorld)
                .underground(undergroundWorld)
                .verticalRange(0, 5)
                .maximumCaveY(3)
                .blocks(BLOCKS)
                .build();
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        target.setBlock(0, 1, 0, GENERATED);

        plan.afterCarvers().patch(target);

        assertEquals(GENERATED.id(), target.getBlock(0, 2, 0).id());
    }

    @Test
    void noodleCavesPoliciesHaveTheRequestedPublicShape() {
        assertSame(NoodleCavesPolicy.underground(), NoodleCavesPolicy.underground());

        NoodleCavesPolicy.Surface policy = NoodleCavesPolicy.surface(biome -> true, true);
        assertTrue(policy.predicate().test(BiomeContext.of(
                ChunkView.of(0, 0), PLAINS, null)));
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
        assertEquals(GENERATED.id(), undergroundTarget.getBlock(0, 1, 0).id());
        underground.afterCarvers().patch(undergroundTarget);
        assertEquals(REFERENCE.id(), undergroundTarget.getBlock(0, 1, 0).id());

        WorldGenerationPlan protectedSurface = configured(UnderillaFactory.absolute(referenceWorld))
                .noodleCaves(NoodleCavesPolicy.surface(biome -> false, false))
                .build();
        TestChunkGrid protectedTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        protectedSurface.afterSurface().patch(protectedTarget);
        assertEquals(GENERATED.id(), protectedTarget.getBlock(0, 1, 0).id());
        protectedSurface.afterCarvers().patch(protectedTarget);
        assertEquals(REFERENCE.id(), protectedTarget.getBlock(0, 1, 0).id());

        WorldGenerationPlan cutSurface = configured(UnderillaFactory.absolute(referenceWorld))
                .noodleCaves(NoodleCavesPolicy.surface(biome -> true, false))
                .build();
        TestChunkGrid cutTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        cutSurface.afterSurface().patch(cutTarget);
        assertEquals(REFERENCE.id(), cutTarget.getBlock(0, 1, 0).id());

        WorldGenerationPlan restoredLiquids = configured(UnderillaFactory.absolute(referenceWorld))
                .noodleCaves(NoodleCavesPolicy.surface(biome -> true, true))
                .build();
        TestChunkGrid liquidsTarget = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        restoredLiquids.afterSurface().patch(liquidsTarget);
        assertEquals(GENERATED.id(), liquidsTarget.getBlock(1, 1, 0).id());
        restoredLiquids.afterCarvers().patch(liquidsTarget);
        assertEquals(WATER.id(), liquidsTarget.getBlock(1, 1, 0).id());
    }

    @Test
    void noodleCavesCanBeSelectedUsingBothWorldsBiomesAndChunks() {
        TestBiome cave = new TestBiome("example:cave");
        TestChunkGrid reference = new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS);
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);
        target.setBiome(0, 1, 0, cave);
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(new TestWorld().addChunk(reference)))
                .surfaceFill(false)
                .noodleCaves(NoodleCavesPolicy.surface(ctx -> {
                    assertEquals(target.getChunkX(), ctx.chunk().x());
                    assertEquals(target.getChunkZ(), ctx.chunk().z());
                    return ctx.source().biome() == PLAINS && ctx.target().biome() == cave;
                }, false))
                .build();

        plan.afterSurface().patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 1, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(4, 1, 0).id());

        plan.afterCarvers().patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(4, 1, 0).id());
    }

    @Test
    void surfacePolicyPatchesTheNegativeWorldBeforeDeferringProtectedReferenceBlocks() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS));
        TestWorld negativeWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS));
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(referenceWorld))
                .underground(negativeWorld)
                .noodleCaves(NoodleCavesPolicy.surface(_ -> false, false))
                .build();
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 4, AIR, PLAINS);

        plan.afterSurface().patch(target);

        assertEquals(GENERATED.id(), target.getBlock(0, 0, 0).id());
        assertEquals(AIR.id(), target.getBlock(0, 1, 0).id());

        plan.afterCarvers().patch(target);

        assertEquals(GENERATED.id(), target.getBlock(0, 0, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, 1, 0).id());
    }

    @Test
    void survivingBlocksOutsideTheMaskAreWrittenBeforeCarversRegardlessOfDeferredCacheResidency() {
        assertEquals(AIR.id(), carvedSurvivingBlock(128).id());
        assertEquals(AIR.id(), carvedSurvivingBlock(1).id());
    }

    @Test
    void protectedBlocksAreRecomputedWhenTheDeferredBatchIsEvicted() {
        TestWorld referenceWorld = new TestWorld()
                .addChunk(new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS))
                .addChunk(new TestChunkGrid(1, 0, 0, 4, REFERENCE, PLAINS));
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(referenceWorld))
                .surfaceFill(false)
                .chunkCacheSize(1)
                .noodleCaves(NoodleCavesPolicy.surface(_ -> false, false))
                .build();
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);

        plan.afterSurface().patch(target);
        assertEquals(GENERATED.id(), target.getBlock(0, 1, 0).id());
        plan.afterSurface().patch(new TestChunkGrid(1, 0, 0, 4, GENERATED, PLAINS));
        target.setBlock(0, 1, 0, AIR);
        plan.afterCarvers().patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 1, 0).id());
    }

    @Test
    void failedSurfaceCollectionLeavesTheCarverPhaseToRecompute() {
        AtomicBoolean fail = new AtomicBoolean(true);
        TestWorld referenceWorld = new TestWorld()
                .addChunk(new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS));
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(referenceWorld))
                .surfaceFill(false)
                .surfaceBlockTransformer(block -> {
                    if (fail.getAndSet(false)) {
                        throw new IllegalStateException("generation failed");
                    }
                    return block;
                })
                .noodleCaves(NoodleCavesPolicy.surface(_ -> false, false))
                .build();
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);

        assertThrows(IllegalStateException.class, () -> plan.afterSurface().patch(target));
        plan.afterCarvers().patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 1, 0).id());
    }

    private static Block carvedSurvivingBlock(int cacheSize) {
        TestBlock ore = TestBlock.solid("minecraft:diamond_ore");
        TestChunkGrid firstReference = new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS);
        firstReference.setBlock(0, 0, 0, ore);
        TestWorld referenceWorld = new TestWorld()
                .addChunk(firstReference)
                .addChunk(new TestChunkGrid(1, 0, 0, 4, REFERENCE, PLAINS));
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(referenceWorld))
                .surfaceFill(false)
                .chunkCacheSize(cacheSize)
                .keptSurfaceBlocks(ctx -> ctx.source().block().id().equals(ore.id()))
                .noodleCaves(NoodleCavesPolicy.surface(_ -> false, false))
                .build();
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);

        plan.afterSurface().patch(target);
        assertEquals(ore.id(), target.getBlock(0, 0, 0).id());
        plan.afterSurface().patch(new TestChunkGrid(1, 0, 0, 4, GENERATED, PLAINS));
        target.setBlock(0, 0, 0, AIR);
        plan.afterCarvers().patch(target);

        return target.getBlock(0, 0, 0);
    }

    @Test
    void surfaceBlockDependenciesAreInjectedAsBehavior() {
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS);
        TestWorld referenceWorld = new TestWorld().addChunk(referenceChunk);
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(referenceWorld))
                .maximumCaveY(2)
                .surfaceBlockTransformer(block -> block.id().equals(REFERENCE.id()) ? WATER : block)
                .keptSurfaceBlocks(ctx -> ctx.source().block().id().equals(WATER.id()))
                .build();
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 4, GENERATED, PLAINS);

        plan.afterCarvers().patch(target);

        assertEquals(WATER.id(), target.getBlock(0, 1, 0).id());
        assertEquals(WATER.id(), target.getBlock(0, 3, 0).id());
    }

    @Test
    void blockSurvivalCanCompareSourceAndTargetBlocksBiomesAndChunks() {
        TestBiome cave = new TestBiome("example:cave");
        TestChunkGrid reference = new TestChunkGrid(-1, -2, 0, 4, REFERENCE, PLAINS);
        TestChunkGrid target = new TestChunkGrid(-1, -2, 0, 4, GENERATED, cave);
        target.setBlock(1, 1, 0, REFERENCE);
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(new TestWorld().addChunk(reference)))
                .maximumCaveY(4)
                .surfaceFill(false)
                .surfaceBlockTransformer(_ -> WATER)
                .keptSurfaceBlocks(ctx -> {
                    assertEquals(target.getChunkX(), ctx.chunk().x());
                    assertEquals(target.getChunkZ(), ctx.chunk().z());
                    assertEquals(ctx.target().block().x(), ctx.source().block().x());
                    assertEquals(ctx.target().block().y(), ctx.source().block().y());
                    assertEquals(ctx.target().block().z(), ctx.source().block().z());
                    assertEquals(-16 + Math.floorMod(ctx.target().block().x(), 16), ctx.source().block().x());
                    assertEquals(-32 + Math.floorMod(ctx.target().block().z(), 16), ctx.source().block().z());
                    return ctx.source().block().id().equals(WATER.id()) && ctx.target().block().id().equals(GENERATED.id())
                            && ctx.source().biome() == PLAINS && ctx.target().biome() == cave;
                })
                .build();

        plan.afterCarvers().patch(target);

        assertEquals(WATER.id(), target.getBlock(0, 1, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(1, 1, 0).id());
        assertEquals(REFERENCE.id(), reference.getBlock(0, 1, 0).id());
    }

    @Test
    void blockPoliciesRetainWorldMetadataThroughVerticalBounds() {
        World source = World.of("reference", ID.of("minecraft:the_nether"), 0, 256);
        World destination = World.of("generated", ID.of("minecraft:overworld"), -4, 8);
        TestChunkGrid reference = new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS);
        TestChunkGrid target = new TestChunkGrid(0, 0, -4, 8, GENERATED, PLAINS).withWorld(destination);
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(
                        new TestWorld().addChunk(reference).withWorld(source)))
                .maximumCaveY(4)
                .surfaceFill(false)
                .keptSurfaceBlocks(ctx -> {
                    assertEquals(source.name(), ctx.source().world().name());
                    assertEquals(source.dimension(), ctx.source().world().dimension());
                    assertSame(destination, ctx.target().world());
                    return ctx.target().world().minHeight() == -4 && ctx.target().world().maxHeight() == 8;
                })
                .build();

        plan.afterCarvers().patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 1, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(0, -1, 0).id());
    }

    @Test
    void finalPlanUsesTheConfiguredBiomeComposition() {
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

        plan.biomePatch().patch(inside);
        plan.biomePatch().patch(outside);

        assertSame(reference, inside.get());
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
    void finalPlanRunsConfiguredBlockCleanupAfterFeatures() {
        TestBlock sand = TestBlock.solid("minecraft:sand");
        TestBlock sandstone = TestBlock.solid("minecraft:sandstone");
        TestBlockFactory blocks = new TestBlockFactory(AIR, GENERATED, REFERENCE, WATER, sand, sandstone);
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS);
        WorldGenerationPlan plan = UnderillaFactory.absolute(new TestWorld().addChunk(referenceChunk))
                .verticalRange(0, 4)
                .maximumCaveY(0)
                .blocks(blocks)
                .blockCleanup(
                    id -> id.equals(ID.of("sand"))
                            ? Optional.of(ID.of("sandstone"))
                            : Optional.empty(),
                    _ -> Optional.empty(),
                    _ -> Optional.empty()
                )
                .build();
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 4, AIR, PLAINS);
        target.setBlock(0, 1, 0, sand);

        assertTrue(plan.tryAfterFeatures(target));

        assertEquals(sandstone.id(), target.getBlock(0, 1, 0).id());
    }

    @Test
    void finalPlanRunsConfiguredEntityCleanupAfterLoad() {
        TestChunkGrid referenceChunk = new TestChunkGrid(0, 0, 0, 4, REFERENCE, PLAINS);
        TestEntity item = new TestEntity("item");
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 4, AIR, PLAINS).addLiveEntity(item);
        WorldGenerationPlan plan = configured(UnderillaFactory.absolute(
                        new TestWorld().addChunk(referenceChunk)))
                .entityCleanup(entity -> entity.id().equals(com.kntrel.mc.underilla.core.api.ID.of("item")), _ -> {})
                .build();

        assertTrue(plan.tryAfterLoad(target));

        assertTrue(item.removed);
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

    private static Patcher<ChunkData> referenceWorldPatcher(
            WorldReader referenceWorld,
            WorldMask worldMask,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            Collection<Patcher<ChunkBlock>> transformations
    ) {
        return ReferenceWorldPatchers.from(
                referenceWorld,
                null,
                worldMask,
                false,
                air,
                survivingBlock == null ? null : ctx -> survivingBlock.test(ctx.source().block()),
                transformations,
                NoodleCavesPolicy.underground(),
                false,
                new ChunkCache(1)
        ).afterCarvers();
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

    private static final class TestEntity implements Entity {
        private final com.kntrel.mc.underilla.core.api.ID id;
        private boolean removed;

        private TestEntity(String id) { this.id = com.kntrel.mc.underilla.core.api.ID.of(id); }

        @Override
        public com.kntrel.mc.underilla.core.api.ID id() { return id; }

        @Override
        public void remove() { removed = true; }
    }
}
