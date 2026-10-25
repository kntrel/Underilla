package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.patch.Patcher;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PatchersBlockCleanupTest {

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock STONE = TestBlock.solid("minecraft:stone");
    private static final TestBlock DIRT = TestBlock.solid("minecraft:dirt");
    private static final TestBlock SAND = TestBlock.solid("minecraft:sand");
    private static final TestBlock SANDSTONE = TestBlock.solid("minecraft:sandstone");
    private static final TestBlock GLASS = TestBlock.solid("minecraft:glass");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");
    private static final TestBlockFactory BLOCKS = new TestBlockFactory(
            AIR, STONE, DIRT, SAND, SANDSTONE, GLASS);

    @Test
    void replacesConfiguredBlocksOnlyWhenTheirSupportIsNotSolid() {
        TestChunkGrid chunk = new TestChunkGrid(0, 0, 0, 3, AIR, PLAINS);
        chunk.setBlock(0, 1, 0, SAND);
        chunk.setBlock(1, 0, 0, STONE);
        chunk.setBlock(1, 1, 0, SAND);

        patcher(Map.of("minecraft:sand", "minecraft:sandstone"), Map.of()).patch(chunk);

        assertEquals(SANDSTONE.id(), chunk.getBlock(0, 1, 0).id());
        assertEquals(SAND.id(), chunk.getBlock(1, 1, 0).id());
    }

    @Test
    void ordinaryReplacementUsesTheOriginalBlockAndOverridesSupportReplacement() {
        TestChunkGrid chunk = new TestChunkGrid(0, 0, 0, 2, AIR, PLAINS);
        chunk.setBlock(0, 1, 0, SAND);

        patcher(
                Map.of("minecraft:sand", "minecraft:sandstone"),
                Map.of("minecraft:sand", "minecraft:glass"))
                .patch(chunk);

        assertEquals(GLASS.id(), chunk.getBlock(0, 1, 0).id());
    }

    @Test
    void doesNotTryToReadBelowTheMinimumBuildHeight() {
        TestChunkGrid chunk = new TestChunkGrid(0, 0, -2, 0, SAND, PLAINS);

        patcher(Map.of("minecraft:sand", "minecraft:sandstone"), Map.of()).patch(chunk);

        assertEquals(SAND.id(), chunk.getBlock(0, -2, 0).id());
    }

    @Test
    void processesEachColumnBottomUpSoLowerReplacementsAffectSupportChecks() {
        TestChunkGrid chunk = new TestChunkGrid(0, 0, 0, 2, AIR, PLAINS);
        chunk.setBlock(0, 0, 0, DIRT);
        chunk.setBlock(0, 1, 0, SAND);

        patcher(
                Map.of("minecraft:sand", "minecraft:sandstone"),
                Map.of("minecraft:dirt", "minecraft:air"))
                .patch(chunk);

        assertEquals(AIR.id(), chunk.getBlock(0, 0, 0).id());
        assertEquals(SANDSTONE.id(), chunk.getBlock(0, 1, 0).id());
    }

    private static Patcher<ChunkData> patcher(Map<String, String> support, Map<String, String> replacements) {
        return Patchers.blockCleanupPatcher(
                BLOCKS,
                id -> java.util.Optional.ofNullable(support.get(id.toString())).map(ID::of),
                id -> java.util.Optional.ofNullable(replacements.get(id.toString())).map(ID::of));
    }
}
