package com.kntrel.mc.underilla.core.api.context;

import com.kntrel.mc.underilla.core.api.World;

/** One side's read-only world metadata. */
public interface WorldScope {

    static WorldScope of(World world) {
        return new Impl.WorldSide(world);
    }

    World world();
}
