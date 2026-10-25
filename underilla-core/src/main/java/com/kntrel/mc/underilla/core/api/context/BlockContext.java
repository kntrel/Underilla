package com.kntrel.mc.underilla.core.api.context;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.World;

/** Pairs source and target block and biome values at shared chunk coordinates. */
public interface BlockContext extends BiomeContext {

    static BlockContext of(
            ChunkView chunk, Biome sourceBiome, Block sourceBlock, Biome targetBiome, Block targetBlock
    ) {
        return of(chunk, null, sourceBiome, sourceBlock, null, targetBiome, targetBlock);
    }

    static BlockContext of(
            ChunkView chunk, World sourceWorld, Biome sourceBiome, Block sourceBlock,
            World targetWorld, Biome targetBiome, Block targetBlock
    ) {
        return new Impl.Blocks(chunk, BlockScope.of(sourceWorld, sourceBiome, sourceBlock),
                BlockScope.of(targetWorld, targetBiome, targetBlock));
    }

    @Override
    BlockScope source();

    @Override
    BlockScope target();
}
