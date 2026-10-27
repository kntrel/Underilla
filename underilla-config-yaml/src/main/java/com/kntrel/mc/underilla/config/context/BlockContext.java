package com.kntrel.mc.underilla.config.context;

import com.kntrel.mc.underilla.config.entity.World;
import com.kntrel.mc.underilla.config.entity.Chunk;
import com.kntrel.mc.underilla.config.entity.Biome;
import com.kntrel.mc.underilla.config.entity.Block;
import com.kntrel.mc.underilla.config.property.Prop;

/** One side's block and surrounding biome/world data. Unavailable values may be null. */
public interface BlockContext extends BiomeContext {
    static BlockContext of(World world, Chunk chunk, Biome biome, Block block) {
        return new Impl.BlockContextValue(world, chunk, biome, block);
    }

    @Prop Block block();
}
