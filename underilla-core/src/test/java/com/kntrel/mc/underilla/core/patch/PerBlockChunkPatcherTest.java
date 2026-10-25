package com.kntrel.mc.underilla.core.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PerBlockChunkPatcherTest {

    private static final TestBlock GENERATED = TestBlock.solid("minecraft:generated");
    private static final TestBlock REFERENCE = TestBlock.solid("minecraft:reference");
    private static final TestBlock MAPPED = TestBlock.solid("minecraft:mapped");
    private static final TestBlock FINAL = TestBlock.solid("minecraft:final");
    private static final TestBlock WATERLOGGABLE = TestBlock.waterloggable("minecraft:test", false);
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");

    @Test
    void positionedCopiesAndClonesKeepCoordinatesWithoutMutatingSharedValues() {
        TestBlock template = TestBlock.waterloggable("minecraft:test", false);
        TestBlock positioned = new TestBlock(template, -17, -3, 48);
        TestBlock clone = positioned.clone();
        clone.waterlog();

        assertEquals(-17, clone.x());
        assertEquals(-3, clone.y());
        assertEquals(48, clone.z());
        assertEquals(0, template.x());
        assertEquals(0, template.y());
        assertEquals(0, template.z());
        assertFalse(template.isWaterlogged());
        assertFalse(positioned.isWaterlogged());
        assertTrue(clone.isWaterlogged());
    }

    @Test
    void candidateAndAttemptProxiesKeepTheirPositionAfterReplacement() {
        TestChunkGrid target = new TestChunkGrid(-2, 3, -4, 1, GENERATED, PLAINS);
        ChunkBlock block = new ChunkBlock(target, 15, -3, 2, GENERATED);
        block.replace(REFERENCE);
        ChunkBlock attempt = block.attempt(MAPPED);
        for (var value : java.util.List.of(block.block(), block.block().clone(),
                attempt.block(), attempt.block().clone())) {
            assertEquals(-17, value.x());
            assertEquals(-3, value.y());
            assertEquals(50, value.z());
        }
        assertFalse(attempt.changed());
        var snapshot = attempt.block().clone();
        attempt.replace(FINAL);
        assertEquals(MAPPED.id(), snapshot.id());
        assertEquals(MAPPED.id(), ChunkBlock.unwrap(snapshot).id());
        target.setBlock(15, -3, 2, snapshot);
        assertEquals(MAPPED.id(), target.getBlock(15, -3, 2).id());
        assertEquals(-17, snapshot.x());
    }

    @Test
    void replaceMarksTheBlockAsChangedEvenWhenItKeepsTheSameInstance() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        ChunkBlock block = new ChunkBlock(target, 0, 0, 0, GENERATED);

        assertFalse(block.changed());

        block.replace(block.block());

        assertTrue(block.changed());
    }

    @Test
    void mutatingTheCandidateThroughItsProxyMarksItAsChanged() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, WATERLOGGABLE, PLAINS);
        ChunkBlock block = new ChunkBlock(target, 0, 0, 0, WATERLOGGABLE);

        block.block().waterlog();

        assertTrue(block.changed());
        assertTrue(WATERLOGGABLE.isWaterlogged());
    }

    @Test
    void identityTransformationDoesNotRequestAWrite() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        ChunkBlock block = new ChunkBlock(target, 0, 0, 0, GENERATED);

        new BlockTransformerPatcher(candidate -> candidate).patch(block);

        assertFalse(block.changed());
    }

    @Test
    void chunkBlockTransformationTreatsNullAndIdentityAsNoOp() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        ChunkBlock nullResult = new ChunkBlock(target, 0, 0, 0, GENERATED);
        ChunkBlock identityResult = new ChunkBlock(target, 1, 0, 0, GENERATED);

        new TransformationBlockPatcher(_ -> null).patch(nullResult);
        new TransformationBlockPatcher(candidate -> candidate).patch(identityResult);

        assertFalse(nullResult.changed());
        assertFalse(identityResult.changed());
    }

    @Test
    void chunkBlockTransformationAdoptsTheReturnedCandidatesBlock() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        ChunkBlock original = new ChunkBlock(target, 0, 0, 0, GENERATED);
        ChunkBlock transformed = new ChunkBlock(target, 0, 0, 0, MAPPED);

        new TransformationBlockPatcher(_ -> transformed).patch(original);

        assertTrue(original.changed());
        assertEquals(MAPPED.id(), original.candidate().id());
    }

    @Test
    void transformsTargetBlocksThroughTheSimplePerBlockConstructor() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);

        new PerBlockChunkPatcher(new BlockTransformerPatcher(_ -> FINAL)).patch(target);

        assertEquals(FINAL.id(), target.getBlock(0, 0, 0).id());
        assertEquals(FINAL.id(), target.getBlock(15, 0, 15).id());
    }

    @Test
    void appliesBlockPatchersInOrderDuringOneTraversal() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS);
        AtomicInteger visitedBlocks = new AtomicInteger();

        PerBlockChunkPatcher patcher = new PerBlockChunkPatcher(
                new BlockTransformerPatcher(_ -> MAPPED),
                block -> {
                    assertEquals(REFERENCE.id(), block.initialBlock().id());
                    assertEquals(MAPPED.id(), block.block().id());
                    block.replace(block.x() == 0 && block.z() == 0 ? FINAL : block.initialBlock());
                    visitedBlocks.incrementAndGet();
                }
        );

        patcher.patch(target);

        assertEquals(FINAL.id(), target.getBlock(0, 0, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(1, 0, 0).id());
        assertEquals(16 * 16, visitedBlocks.get());
    }

    @Test
    void traversesFromTheChunksMinimumHeight() {
        TestChunkGrid target = new TestChunkGrid(0, 0, -2, 2, GENERATED, PLAINS);
        PerBlockChunkPatcher patcher = new PerBlockChunkPatcher(new BlockTransformerPatcher(_ -> REFERENCE));

        patcher.patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, -2, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, -1, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, 1, 0).id());
    }

    @Test
    void exposesGlobalCoordinatesAndTheExistingDestinationBlock() {
        TestChunkGrid target = new TestChunkGrid(-2, 3, 0, 1, GENERATED, PLAINS);
        PerBlockChunkPatcher patcher = new PerBlockChunkPatcher(
                block -> {
                    if (block.globalX() == -32
                            && block.globalZ() == 48
                            && block.destinationBlock().id().equals(GENERATED.id())) {
                        block.replace(REFERENCE);
                    }
                }
        );

        patcher.patch(target);

        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(1, 0, 0).id());
    }

    @Test
    void routingBuilderCommitsAcceptedUpstreamChangesBeforeRunningTheSelectedRoute() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        ChunkBlock block = new ChunkBlock(target, 0, 0, 0, GENERATED);
        RoutingBlockPatcher router = RoutingBlockPatcher.builder()
                .upstream(candidate -> candidate.replace(REFERENCE))
                .upstream(new BlockTransformerPatcher(_ -> MAPPED))
                .test((original, candidate) ->
                        original.block().id().equals(GENERATED.id())
                                && candidate.id().equals(MAPPED.id()))
                .ifTrue(candidate -> {
                    assertEquals(MAPPED.id(), candidate.block().id());
                    candidate.replace(FINAL);
                })
                .build();

        router.patch(block);

        assertTrue(block.changed());
        assertEquals(FINAL.id(), block.block().id());
    }

    @Test
    void rollbackDiscardsSpeculativeReplacements() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        ChunkBlock block = new ChunkBlock(target, 0, 0, 0, GENERATED);
        RoutingBlockPatcher router = RoutingBlockPatcher.builder()
                .upstream(candidate -> candidate.replace(REFERENCE))
                .test((_, _) -> false)
                .ifFalse(RoutingBlockPatcher.ROLLBACK)
                .build();

        router.patch(block);

        assertFalse(block.changed());
        assertEquals(GENERATED.id(), block.block().id());
    }

    @Test
    void rollbackIsolatesInPlaceMutationsWithoutCloningForReads() {
        TestBlock original = TestBlock.waterloggable("minecraft:rollback_test", false);
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, original, PLAINS);
        ChunkBlock block = new ChunkBlock(target, 0, 0, 0, original);
        RoutingBlockPatcher router = RoutingBlockPatcher.builder()
                .upstream(candidate -> {
                    assertTrue(candidate.block().isWaterloggable());
                    candidate.block().waterlog();
                })
                .test((_, _) -> false)
                .build();

        router.patch(block);

        assertFalse(block.changed());
        assertFalse(original.isWaterlogged());
    }

    @Test
    void aRegularFalseRouteCommitsTheSubjectBeforeRunningDownstream() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);
        ChunkBlock block = new ChunkBlock(target, 0, 0, 0, GENERATED);
        RoutingBlockPatcher router = RoutingBlockPatcher.builder()
                .upstream(candidate -> candidate.replace(REFERENCE))
                .test((_, _) -> false)
                .ifFalse(candidate -> {
                    assertEquals(REFERENCE.id(), candidate.block().id());
                    candidate.replace(FINAL);
                })
                .build();

        router.patch(block);

        assertTrue(block.changed());
        assertEquals(FINAL.id(), block.block().id());
    }
}
