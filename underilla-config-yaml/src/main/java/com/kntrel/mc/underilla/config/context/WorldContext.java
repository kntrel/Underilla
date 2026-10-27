package com.kntrel.mc.underilla.config.context;

import com.kntrel.mc.underilla.config.entity.World;
import com.kntrel.mc.underilla.config.entity.Chunk;
import com.kntrel.mc.underilla.config.property.Prop;

/** One side's world metadata and chunk coordinates. Unavailable values may be null. */
public interface WorldContext {
    static WorldContext of(World world, Chunk chunk) {
        return new Impl.WorldContextValue(world, chunk);
    }

    @Prop World world();
    @Prop Chunk chunk();
}
