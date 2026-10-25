package com.kntrel.mc.underilla.core.api.context;

import com.kntrel.mc.underilla.core.api.World;

/** The shared chunk coordinates for a scoped policy. */
public interface ChunkContext {

    static ChunkContext of(ChunkView chunk) {
        return of(chunk, null, null);
    }

    static ChunkContext of(ChunkView chunk, World sourceWorld, World targetWorld) {
        return new Impl.Chunks(chunk, WorldScope.of(sourceWorld), WorldScope.of(targetWorld));
    }

    ChunkView chunk();

    WorldScope source();

    WorldScope target();
}
