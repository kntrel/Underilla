package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.World;

/** Source and target world scopes for a decision. */
public interface DualWorldScope {

    static DualWorldScope of(WorldScope source, WorldScope target) {
        return new Impl.DualWorlds(source, target);
    }

    static DualWorldScope of(ChunkSnapshot chunk) {
        return of(chunk, null, null);
    }

    static DualWorldScope of(ChunkSnapshot chunk, World sourceWorld, World targetWorld) {
        return of(WorldScope.of(WorldSnapshot.of(sourceWorld), chunk),
                WorldScope.of(WorldSnapshot.of(targetWorld), chunk));
    }

    WorldScope source();

    WorldScope target();
}
