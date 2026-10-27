package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.World;

/** Source and target block scopes for a decision. */
public interface DualBlockScope extends DualBiomeScope {

    static DualBlockScope of(BlockScope source, BlockScope target) {
        return new Impl.DualBlocks(source, target);
    }

    static DualBlockScope of(
            ChunkSnapshot chunk, Biome sourceBiome, Block sourceBlock, Biome targetBiome, Block targetBlock
    ) {
        return of(chunk, null, sourceBiome, sourceBlock, null, targetBiome, targetBlock);
    }

    static DualBlockScope of(
            ChunkSnapshot chunk, World sourceWorld, Biome sourceBiome, Block sourceBlock,
            World targetWorld, Biome targetBiome, Block targetBlock
    ) {
        return of(BlockScope.of(sourceWorld, chunk, sourceBiome, sourceBlock),
                BlockScope.of(targetWorld, chunk, targetBiome, targetBlock));
    }

    @Override
    BlockScope source();

    @Override
    BlockScope target();
}
