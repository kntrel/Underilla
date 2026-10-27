package com.kntrel.mc.underilla.config.context;

import com.kntrel.mc.underilla.config.entity.World;
import com.kntrel.mc.underilla.config.entity.Chunk;
import com.kntrel.mc.underilla.config.entity.Entity;
import com.kntrel.mc.underilla.config.property.Prop;

/** One side's entity candidate and world data. Unavailable values may be null. */
public interface EntityContext extends WorldContext {
    static EntityContext of(World world, Chunk chunk, Entity entity) {
        return new Impl.EntityContextValue(world, chunk, entity);
    }

    @Prop Entity entity();
}
