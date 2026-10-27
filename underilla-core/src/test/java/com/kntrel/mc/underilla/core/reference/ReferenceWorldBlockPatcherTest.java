package com.kntrel.mc.underilla.core.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kntrel.mc.underilla.core.api.scope.DualBlockScope;
import com.kntrel.mc.underilla.core.api.scope.ChunkSnapshot;
import com.kntrel.mc.underilla.core.api.scope.WorldSnapshot;
import java.util.function.Predicate;
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
    void replacesOnlyBlocksAcceptedByTheSnapshotPredicate() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(-2, 3, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(-2, 3, 0, 1, GENERATED, PLAINS);
        Predicate<DualBlockScope> selectedColumn = snapshot ->
                snapshot.target().block().x() == -32 && snapshot.target().block().z() == 48
                        && snapshot.source().block().x() == -32 && snapshot.source().block().z() == 48
                        && snapshot.source().block().id().equals(REFERENCE.id())
                        && snapshot.source().world().equals(WorldSnapshot.of(referenceWorld))
                        && snapshot.target().world().equals(WorldSnapshot.of(target.world()))
                        && snapshot.source().biome().id().equals(PLAINS.id())
                        && snapshot.target().biome().id().equals(PLAINS.id());

        patcher(referenceWorld, selectedColumn).patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(1, 0, 0).id());
    }

    @Test
    void exposesAirWhenTheReferenceChunkHasNoBlockAtTheTargetHeight() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, -1, 1, GENERATED, PLAINS);

        patcher(referenceWorld, snapshot -> snapshot.source().block().isAir()).patch(target);

        assertEquals(AIR.id(), target.getBlock(0, -1, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(0, 0, 0).id());
    }

    @Test
    void snapshotPredicatesCompose() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        Predicate<DualBlockScope> firstColumn = snapshot -> snapshot.target().block().x() == 0;
        Predicate<DualBlockScope> secondColumn = snapshot -> snapshot.target().block().x() == 1;
        Predicate<DualBlockScope> selectedColumns = firstColumn.or(secondColumn);

        patcher(referenceWorld, selectedColumns).patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(1, 0, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(2, 0, 0).id());
    }

    @Test
    void oneInstanceReadsAcrossTheWholeReferenceWorld() {
        TestWorld referenceWorld = new TestWorld()
                .addChunk(new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS))
                .addChunk(new TestChunkGrid(1, 0, 0, 1, SECOND_REFERENCE, PLAINS));
        TestChunkGrid firstTarget = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        TestChunkGrid secondTarget = new TestChunkGrid(1, 0, 0, 1, GENERATED, PLAINS);
        PerBlockChunkPatcher patcher = patcher(referenceWorld, _ -> true);

        patcher.patch(firstTarget);
        patcher.patch(secondTarget);

        assertEquals(REFERENCE.id(), firstTarget.getBlock(0, 0, 0).id());
        assertEquals(SECOND_REFERENCE.id(), secondTarget.getBlock(0, 0, 0).id());
    }

    @Test
    void worldMaskTestSelectsUsingTargetGlobalCoordinates() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(-2, 3, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(-2, 3, 0, 1, GENERATED, PLAINS);
        Predicate<DualBlockScope> masked = ReferenceWorldTests.insideMask(
                (x, y, z) -> x == -32 && y == 0 && z == 48);

        patcher(referenceWorld, masked).patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(1, 0, 0).id());
    }

    @Test
    void survivingBlocksAreWrittenOutsideTheMaskOnlyOverSolidTerrain() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        target.setBlock(1, 0, 0, NON_SOLID);
        Predicate<DualBlockScope> inMask = ReferenceWorldTests.insideMask((x, y, z) -> x == 2);
        Predicate<DualBlockScope> shouldWrite = inMask.or(ReferenceWorldTests.survivingOutsideMask(
                snapshot -> snapshot.source().block().id().equals(REFERENCE.id())));

        patcher(referenceWorld, shouldWrite).patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
        assertEquals(NON_SOLID.id(), target.getBlock(1, 0, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(2, 0, 0).id());
    }

    @Test
    void nonSurvivingReferenceBlocksAreNotWrittenOutsideTheMask() {
        TestWorld referenceWorld = new TestWorld().addChunk(
                new TestChunkGrid(0, 0, 0, 1, SECOND_REFERENCE, PLAINS));
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);

        patcher(referenceWorld,
                ReferenceWorldTests.survivingOutsideMask(snapshot -> snapshot.source().block().id().equals(REFERENCE.id()))).patch(target);

        assertEquals(GENERATED.id(), target.getBlock(0, 0, 0).id());
    }

    private static PerBlockChunkPatcher patcher(
            TestWorld referenceWorld,
            Predicate<DualBlockScope> test
    ) {
        return new PerBlockChunkPatcher(RoutingBlockPatcher.builder()
                .upstream(new ReferenceWorldBlockPatcher(referenceWorld, () -> AIR))
                .test((target, proposed) -> test.test(DualBlockScope.of(
                                ChunkSnapshot.of(target.targetChunk()), referenceWorld,
                                referenceWorld.biomeAt(target.globalX(), target.y(), target.globalZ()).orElse(null),
                                target.attempt(proposed).block(), target.targetChunk().world(),
                                target.targetChunk().getBiome(target.x(), target.y(), target.z()), target.candidate())))
                .build());
    }
}
