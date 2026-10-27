package com.kntrel.mc.underilla.core.api.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.World;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScopeTest {

    @Test
    void blockScopeProvidesCapturedValuesThroughEveryInheritedScope() {
        WorldSnapshot world = new WorldSnapshot("reference", ID.of("minecraft:overworld"), -64, 320);
        ChunkSnapshot chunk = ChunkSnapshot.of(-2, 3);
        BiomeSnapshot biome = BiomeSnapshot.of(new TestBiome("minecraft:plains"));
        BlockSnapshot block = BlockSnapshot.of(TestBlock.solid("minecraft:stone"));

        BlockScope blockScope = BlockScope.of(world, chunk, biome, block);
        BiomeScope biomeScope = blockScope;
        WorldScope worldScope = biomeScope;

        assertSame(world, worldScope.world());
        assertSame(chunk, worldScope.chunk());
        assertSame(biome, biomeScope.biome());
        assertSame(block, blockScope.block());
    }

    @Test
    void dualScopeFactoriesSupplyTheSharedChunkToBothSidesAtEveryLevel() {
        ChunkSnapshot chunk = ChunkSnapshot.of(-2, 3);
        World sourceWorld = World.of("reference", ID.of("minecraft:overworld"), -64, 320);
        World targetWorld = World.of("generated", ID.of("minecraft:the_nether"), 0, 256);
        TestBiome sourceBiome = new TestBiome("minecraft:plains");
        TestBiome targetBiome = new TestBiome("minecraft:desert");
        TestBlock sourceBlock = TestBlock.solid("minecraft:stone");
        TestBlock targetBlock = TestBlock.air("minecraft:air");
        List<DualWorldScope> scopes = List.of(
                DualWorldScope.of(chunk, sourceWorld, targetWorld),
                DualBiomeScope.of(chunk, sourceWorld, sourceBiome, targetWorld, targetBiome),
                DualBlockScope.of(chunk, sourceWorld, sourceBiome, sourceBlock,
                        targetWorld, targetBiome, targetBlock));

        for (DualWorldScope scope : scopes) {
            assertSame(chunk, scope.source().chunk());
            assertSame(chunk, scope.target().chunk());
            assertEquals(WorldSnapshot.of(sourceWorld), scope.source().world());
            assertEquals(WorldSnapshot.of(targetWorld), scope.target().world());
        }
    }

    @Test
    void dualBlockScopeRetainsEachSideThroughEveryInheritedScope() {
        WorldSnapshot sourceWorld = new WorldSnapshot("reference", ID.of("minecraft:overworld"), -64, 320);
        WorldSnapshot targetWorld = new WorldSnapshot("generated", ID.of("minecraft:the_nether"), 0, 256);
        ChunkSnapshot chunk = ChunkSnapshot.of(-2, 3);
        BiomeSnapshot sourceBiome = BiomeSnapshot.of(new TestBiome("minecraft:plains"));
        BiomeSnapshot targetBiome = BiomeSnapshot.of(new TestBiome("minecraft:desert"));
        BlockScope source = BlockScope.of(sourceWorld, chunk, sourceBiome,
                BlockSnapshot.of(TestBlock.solid("minecraft:stone")));
        BlockScope target = BlockScope.of(targetWorld, chunk, targetBiome,
                BlockSnapshot.of(TestBlock.air("minecraft:air")));

        DualBlockScope blocks = DualBlockScope.of(source, target);
        DualBiomeScope biomes = blocks;
        DualWorldScope worlds = biomes;

        assertSame(source, worlds.source());
        assertSame(target, worlds.target());
        assertSame(sourceBiome, biomes.source().biome());
        assertSame(targetBiome, biomes.target().biome());
        assertSame(source.block(), blocks.source().block());
        assertSame(target.block(), blocks.target().block());
        assertSame(chunk, worlds.source().chunk());
        assertSame(chunk, biomes.source().chunk());
        assertSame(chunk, blocks.source().chunk());
        assertSame(chunk, worlds.target().chunk());
    }
}
