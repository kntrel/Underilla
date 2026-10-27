package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.reader.ChunkReader;

/** Read-only horizontal coordinates of one chunk, without access to mutable chunk contents. */
public record ChunkSnapshot(int x, int z) {

    public static ChunkSnapshot of(int x, int z) {
        return new ChunkSnapshot(x, z);
    }

    public static ChunkSnapshot of(ChunkReader reader) {
        return of(reader.getX(), reader.getZ());
    }

    public static ChunkSnapshot of(ChunkData data) {
        return of(data.getChunkX(), data.getChunkZ());
    }
}
