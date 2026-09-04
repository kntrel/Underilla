package com.kntrel.mc.underilla.core.patch;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import org.junit.jupiter.api.Test;

class DeferredPatcherTest {

    private static final TestBlock GENERATED = TestBlock.solid("minecraft:generated");
    private static final TestBlock REFERENCE = TestBlock.solid("minecraft:reference");
    private static final TestBlock OTHER_REFERENCE = TestBlock.solid("minecraft:other_reference");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");

    @Test
    void replaysOnlySelectedWritesAfterCarversAndConsumesThem() {
        TestChunkGrid chunk = new TestChunkGrid(4, -2, 0, 4, GENERATED, PLAINS);
        ChunkPatcher writesReferenceTerrain = target -> {
            target.setRegion(0, 1, 0, 2, 2, 1, REFERENCE);
            target.setBlock(2, 1, 0, OTHER_REFERENCE);
        };
        DeferredPatcher deferred = new DeferredPatcher(writesReferenceTerrain,
                (position, ignored) -> position.x() == 0);
        ChunkPatcher restore = deferred.applier();

        deferred.patch(chunk);

        assertSame(GENERATED, chunk.getBlock(0, 1, 0));
        assertSame(REFERENCE, chunk.getBlock(1, 1, 0));
        assertSame(OTHER_REFERENCE, chunk.getBlock(2, 1, 0));

        restore.patch(chunk);

        assertSame(REFERENCE, chunk.getBlock(0, 1, 0));
        assertSame(REFERENCE, chunk.getBlock(1, 1, 0));
        assertSame(OTHER_REFERENCE, chunk.getBlock(2, 1, 0));

        chunk.setBlock(0, 1, 0, GENERATED);
        restore.patch(chunk);
        assertSame(GENERATED, chunk.getBlock(0, 1, 0));
    }
}
