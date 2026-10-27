package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.World;

/** One side's read-only world metadata and chunk coordinates. */
public interface WorldScope {

    static WorldScope of(World world) {
        return of(WorldSnapshot.of(world));
    }

    static WorldScope of(WorldSnapshot world) {
        return of(world, null);
    }

    static WorldScope of(WorldSnapshot world, ChunkSnapshot chunk) {
        return new Impl.WorldSide(world, chunk);
    }

    /** {@return the captured world metadata, or null when unavailable during this decision} */
    WorldSnapshot world();

    /** {@return the captured chunk coordinates, or null when unavailable during this decision} */
    ChunkSnapshot chunk();
}
