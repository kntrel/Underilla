package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import java.util.concurrent.atomic.AtomicReference;

import com.kntrel.mc.underilla.core.reference.WorldHeightMaskPatcher;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import org.junit.jupiter.api.Test;

class WorldHeightMaskPatcherTest {

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock STONE = TestBlock.solid("minecraft:stone");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");

    @Test
    void exposesTheChunkHeightMaskOnlyWhileTheDelegateRuns() {
        AtomicReference<WorldMask> capturedMask = new AtomicReference<>();
        WorldHeightMaskPatcher patcher = new WorldHeightMaskPatcher(0, mask -> {
            capturedMask.set(mask);
            return _ -> {
                assertFalse(mask.contains(0, 2, 0));
                assertTrue(mask.contains(0, 3, 0));
            };
        });
        TestChunkGrid target = targetChunk();

        assertFalse(capturedMask.get().contains(0, 3, 0));
        patcher.patch(target);
        assertFalse(capturedMask.get().contains(0, 3, 0));
    }

    @Test
    void clearsTheChunkHeightMaskWhenTheDelegateFails() {
        AtomicReference<WorldMask> capturedMask = new AtomicReference<>();
        WorldHeightMaskPatcher patcher = new WorldHeightMaskPatcher(0, mask -> {
            capturedMask.set(mask);
            return _ -> {
                throw new IllegalStateException("failure");
            };
        });

        assertThrows(IllegalStateException.class, () -> patcher.patch(targetChunk()));

        assertFalse(capturedMask.get().contains(0, 3, 0));
    }

    private static TestChunkGrid targetChunk() {
        TestChunkGrid target = new TestChunkGrid(0, 0, 0, 5, AIR, PLAINS);
        target.fillColumn(0, 0, 0, 3, STONE);
        return target;
    }
}
