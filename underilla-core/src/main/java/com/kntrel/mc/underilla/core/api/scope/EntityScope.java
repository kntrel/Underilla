package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.Entity;
import com.kntrel.mc.underilla.core.api.World;

/** One world's entity, chunk coordinates, and metadata for a decision. */
public interface EntityScope extends WorldScope {

    static EntityScope of(Entity entity) {
        return of(null, entity);
    }

    static EntityScope of(World world, Entity entity) {
        return of(WorldSnapshot.of(world), EntitySnapshot.of(entity));
    }

    static EntityScope of(WorldSnapshot world, EntitySnapshot entity) {
        return of(world, null, entity);
    }

    static EntityScope of(WorldSnapshot world, ChunkSnapshot chunk, EntitySnapshot entity) {
        return new Impl.EntitySide(world, chunk, entity);
    }

    /** {@return the captured entity, or null when unavailable during this decision} */
    EntitySnapshot entity();
}
