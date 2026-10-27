package com.kntrel.mc.underilla.config.context;

import com.kntrel.mc.underilla.config.entity.World;
import com.kntrel.mc.underilla.config.entity.Chunk;
import com.kntrel.mc.underilla.config.entity.Biome;
import com.kntrel.mc.underilla.config.property.Prop;

/** One side's biome sample, world metadata, and chunk coordinates. Unavailable values may be null. */
public interface BiomeContext extends WorldContext {
    static BiomeContext of(World world, Chunk chunk, Biome biome) {
        return new Impl.BiomeContextValue(world, chunk, biome);
    }

    @Prop Biome biome();
}
