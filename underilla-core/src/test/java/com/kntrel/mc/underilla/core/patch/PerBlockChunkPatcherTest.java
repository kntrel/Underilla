package com.kntrel.mc.underilla.core.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertSame(MAPPED, original.candidate());
    }

    @Test
    void transformsTargetBlocksThroughTheSimplePerBlockConstructor() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, GENERATED, PLAINS);

        new PerBlockChunkPatcher(new BlockTransformerPatcher(_ -> FINAL)).patch(target);

        assertSame(FINAL, target.getBlock(0, 0, 0));
        assertSame(FINAL, target.getBlock(15, 0, 15));
    }

    @Test
    void appliesBlockPatchersInOrderDuringOneTraversal() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 1, REFERENCE, PLAINS);
        AtomicInteger visitedBlocks = new AtomicInteger();

        PerBlockChunkPatcher patcher = new PerBlockChunkPatcher(
                new BlockTransformerPatcher(_ -> MAPPED),
                block -> {
                    assertSame(REFERENCE, block.initialBlock());
                    assertEquals(MAPPED.id(), block.block().id());
                    block.replace(block.x() == 0 && block.z() == 0 ? FINAL : block.initialBlock());
                    visitedBlocks.incrementAndGet();
                }
        );

        patcher.patch(target);

        assertSame(FINAL, target.getBlock(0, 0, 0));
        assertSame(REFERENCE, target.getBlock(1, 0, 0));
        assertEquals(16 * 16, visitedBlocks.get());
    }

    @Test
    void traversesFromTheChunksMinimumHeight() {
        TestChunkGrid target = new TestChunkGrid(0, 0, -2, 2, GENERATED, PLAINS);
        PerBlockChunkPatcher patcher = new PerBlockChunkPatcher(new BlockTransformerPatcher(_ -> REFERENCE));

        patcher.patch(target);

        assertSame(REFERENCE, target.getBlock(0, -2, 0));
        assertSame(REFERENCE, target.getBlock(0, -1, 0));
        assertSame(REFERENCE, target.getBlock(0, 0, 0));
        assertSame(REFERENCE, target.getBlock(0, 1, 0));
    }

    @Test
    void exposesGlobalCoordinatesAndTheExistingDestinationBlock() {
        TestChunkGrid target = new TestChunkGrid(-2, 3, 0, 1, GENERATED, PLAINS);
        PerBlockChunkPatcher patcher = new PerBlockChunkPatcher(
                block -> {
                    if (block.globalX() == -32
                            && block.globalZ() == 48
                            && block.destinationBlock() == GENERATED) {
                        block.replace(REFERENCE);
                    }
                }
        );

        patcher.patch(target);

        assertSame(REFERENCE, target.getBlock(0, 0, 0));
        assertSame(GENERATED, target.getBlock(1, 0, 0));
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
