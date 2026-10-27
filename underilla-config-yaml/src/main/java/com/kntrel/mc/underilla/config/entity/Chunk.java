package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.scope.ChunkSnapshot;

/** Read-only chunk location in its context's world. Coordinates are chunk coordinates. */
public non-sealed interface Chunk extends ConfigEntity {
    static Chunk of(ChunkSnapshot snapshot, World world) { return new Impl.ChunkValue(snapshot, world); }

    @Prop World world();
    @Prop int x();
    @Prop int z();
    @Prop default int minX() { return x() * GenerationConstants.CHUNK_SIZE; }
    @Prop default int minZ() { return z() * GenerationConstants.CHUNK_SIZE; }
    @Prop default int maxX() { return minX() + GenerationConstants.CHUNK_SIZE; }
    @Prop default int maxZ() { return minZ() + GenerationConstants.CHUNK_SIZE; }
}
