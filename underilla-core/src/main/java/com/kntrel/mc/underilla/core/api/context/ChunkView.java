package com.kntrel.mc.underilla.core.api.context;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.reader.ChunkReader;

/** The two horizontal chunk coordinates, without access to chunk contents. */
public interface ChunkView {

    static ChunkView of(int x, int z) {
        return new Impl.ChunkPosition(x, z);
    }

    static ChunkView of(ChunkReader reader) {
        return of(reader.getX(), reader.getZ());
    }

    static ChunkView of(ChunkData data) {
        return of(data.getChunkX(), data.getChunkZ());
    }

    int x();

    int z();
}
