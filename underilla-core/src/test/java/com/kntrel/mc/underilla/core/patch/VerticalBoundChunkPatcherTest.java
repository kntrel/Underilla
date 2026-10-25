package com.kntrel.mc.underilla.core.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VerticalBoundChunkPatcherTest {

    private static final TestBlock GENERATED = TestBlock.solid("minecraft:generated");
    private static final TestBlock REFERENCE = TestBlock.solid("minecraft:reference");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");

    @Test
    void limitsPerBlockTraversalThroughTheChunksAdvertisedHeights() {
        TestChunkGrid target = new TestChunkGrid(0, 0, -2, 3, GENERATED, PLAINS);
        Patcher<ChunkData> blocks = new PerBlockChunkPatcher(new BlockTransformerPatcher(_ -> REFERENCE));

        new VerticalBoundChunkPatcher(blocks, -1, 2).patch(target);

        assertEquals(GENERATED.id(), target.getBlock(0, -2, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, -1, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, 0, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, 1, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(0, 2, 0).id());
    }

    @Test
    void clampsTheAdvertisedRangeToThePhysicalChunkBounds() {
        TestChunkGrid target = new TestChunkGrid(0, 0, -2, 3, GENERATED, PLAINS);
        Patcher<ChunkData> assertBounds = chunk -> {
            assertEquals(-2, chunk.getMinHeight());
            assertEquals(1, chunk.getMaxHeight());
        };

        new VerticalBoundChunkPatcher(assertBounds, -10, 1).patch(target);
    }

    @Test
    void passesThroughTheOriginalChunkWhenTheRangeContainsIt() {
        TestChunkGrid target = new TestChunkGrid(0, 0, -2, 3, GENERATED, PLAINS);
        AtomicReference<ChunkData> received = new AtomicReference<>();

        new VerticalBoundChunkPatcher(received::set, -10, 10).patch(target);

        assertSame(target, received.get());
    }

    @Test
    void rejectsReadsOutsideTheAdvertisedRange() {
        TestChunkGrid target = new TestChunkGrid(0, 0, -2, 3, GENERATED, PLAINS);
        Patcher<ChunkData> read = chunk -> {
            assertEquals(GENERATED.id(), chunk.getBlock(0, -1, 0).id());
            assertSame(PLAINS, chunk.getBiome(0, 1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> chunk.getBlock(0, -2, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> chunk.getBiome(0, 2, 0));
        };

        new VerticalBoundChunkPatcher(read, -1, 2).patch(target);
    }

    @Test
    void rejectsWritesOutsideTheAdvertisedRange() {
        TestChunkGrid target = new TestChunkGrid(0, 0, -2, 3, GENERATED, PLAINS);
        Patcher<ChunkData> write = chunk -> {
            chunk.setBlock(0, 0, 0, REFERENCE);
            chunk.setRegion(0, -1, 0, 1, 2, 1, REFERENCE);
            assertThrows(IndexOutOfBoundsException.class, () -> chunk.setBlock(0, -2, 0, REFERENCE));
            assertThrows(IndexOutOfBoundsException.class, () -> chunk.setBiome(0, 2, 0, PLAINS));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> chunk.setRegion(0, -2, 0, 1, 0, 1, REFERENCE));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> chunk.setRegion(0, 0, 0, 1, 3, 1, REFERENCE));
        };

        new VerticalBoundChunkPatcher(write, -1, 2).patch(target);

        assertEquals(GENERATED.id(), target.getBlock(0, -2, 0).id());
        assertEquals(REFERENCE.id(), target.getBlock(0, 1, 0).id());
        assertEquals(GENERATED.id(), target.getBlock(0, 2, 0).id());
    }

    @Test
    void rejectsAnInvertedRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new VerticalBoundChunkPatcher(_ -> {}, 2, 1));
    }
}
