package com.kntrel.mc.underilla.core.api.context;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.World;

/** One world's block and sampled biome. */
public interface BlockScope extends BiomeScope {

    static BlockScope of(Biome biome, Block block) {
        return of(null, biome, block);
    }

    static BlockScope of(World world, Biome biome, Block block) {
        return new Impl.BlockSide(world, biome, block);
    }

    /** {@return the supplied block, or null when unavailable during this decision} */
    Block block();
}
