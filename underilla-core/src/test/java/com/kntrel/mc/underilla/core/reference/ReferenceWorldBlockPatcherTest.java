package com.kntrel.mc.underilla.core.reference;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.patch.PerBlockChunkPatcher;
import com.kntrel.mc.underilla.core.patch.RoutingBlockPatcher;
import org.junit.jupiter.api.Test;

class ReferenceWorldBlockPatcherTest {

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock GENERATED = TestBlock.solid("minecraft:generated");
    private static final TestBlock NON_SOLID = TestBlock.nonSolid("minecraft:non_solid");
    private static final TestBlock REFERENCE = TestBlock.solid("minecraft:reference");
    private static final TestBlock SECOND_REFERENCE = TestBlock.solid("minecraft:second_reference");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");

    @Test
    void replacesOnlyBlocksAcceptedByTheReferenceWorldTest() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(-2, 3, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(-2, 3, 0, 1, GENERATED, PLAINS);
        ReferenceWorldTest selectedColumn = (targetBlock, referenceBlock) ->
                targetBlock.globalX() == -32
                        && targetBlock.globalZ() == 48
                        && referenceBlock == REFERENCE;

        patcher(referenceWorld, selectedColumn).patch(target);

        assertSame(REFERENCE, target.getBlock(0, 0, 0));
        assertSame(GENERATED, target.getBlock(1, 0, 0));
    }

    @Test
    void exposesAirWhenTheReferenceChunkHasNoBlockAtTheTargetHeight() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, -1, 1, GENERATED, PLAINS);

        patcher(referenceWorld, (_, referenceBlock) -> referenceBlock.isAir()).patch(target);

        assertSame(AIR, target.getBlock(0, -1, 0));
        assertSame(GENERATED, target.getBlock(0, 0, 0));
    }

    @Test
    void referenceWorldTestsComposeWithoutLosingTheirType() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        ReferenceWorldTest firstColumn = (targetBlock, _) -> targetBlock.x() == 0;
        ReferenceWorldTest secondColumn = (targetBlock, _) -> targetBlock.x() == 1;
        ReferenceWorldTest selectedColumns = firstColumn.or(secondColumn);

        patcher(referenceWorld, selectedColumns).patch(target);

        assertSame(REFERENCE, target.getBlock(0, 0, 0));
        assertSame(REFERENCE, target.getBlock(1, 0, 0));
        assertSame(GENERATED, target.getBlock(2, 0, 0));
    }

    @Test
    void oneInstanceReadsAcrossTheWholeReferenceWorld() {
        TestWorld referenceWorld = new TestWorld()
                .addChunk(new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS))
                .addChunk(new TestChunkGrid(1, 0, 0, 1, SECOND_REFERENCE, PLAINS));
        TestChunkGrid firstTarget = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        TestChunkGrid secondTarget = new TestChunkGrid(1, 0, 0, 1, GENERATED, PLAINS);
        PerBlockChunkPatcher patcher = patcher(referenceWorld, (_, _) -> true);

        patcher.patch(firstTarget);
        patcher.patch(secondTarget);

        assertSame(REFERENCE, firstTarget.getBlock(0, 0, 0));
        assertSame(SECOND_REFERENCE, secondTarget.getBlock(0, 0, 0));
    }

    @Test
    void worldMaskTestSelectsUsingTargetGlobalCoordinates() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(-2, 3, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(-2, 3, 0, 1, GENERATED, PLAINS);
        ReferenceWorldTest masked = ReferenceWorldTests.insideMask(
                (x, y, z) -> x == -32 && y == 0 && z == 48);

        patcher(referenceWorld, masked).patch(target);

        assertSame(REFERENCE, target.getBlock(0, 0, 0));
        assertSame(GENERATED, target.getBlock(1, 0, 0));
    }

    @Test
    void survivingBlocksAreWrittenOutsideTheMaskOnlyOverSolidTerrain() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        target.setBlock(1, 0, 0, NON_SOLID);
        ReferenceWorldTest inMask = ReferenceWorldTests.insideMask((x, y, z) -> x == 2);
        ReferenceWorldTest shouldWrite = inMask.or(
                ReferenceWorldTests.survivingOutsideMask(block -> block == REFERENCE));

        patcher(referenceWorld, shouldWrite).patch(target);

        assertSame(REFERENCE, target.getBlock(0, 0, 0));
        assertSame(NON_SOLID, target.getBlock(1, 0, 0));
        assertSame(REFERENCE, target.getBlock(2, 0, 0));
    }

    @Test
    void nonSurvivingReferenceBlocksAreNotWrittenOutsideTheMask() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 1, SECOND_REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);

        patcher(referenceWorld,
                ReferenceWorldTests.survivingOutsideMask(block -> block == REFERENCE)).patch(target);

        assertSame(GENERATED, target.getBlock(0, 0, 0));
    }

    private static PerBlockChunkPatcher patcher(
            TestWorld referenceWorld,
            ReferenceWorldTest test
    ) {
        return new PerBlockChunkPatcher(RoutingBlockPatcher.builder()
                .upstream(new ReferenceWorldBlockPatcher(referenceWorld, () -> AIR))
                .test(test)
                .build());
    }
}
