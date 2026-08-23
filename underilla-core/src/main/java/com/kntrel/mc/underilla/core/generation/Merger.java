package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;

/** Strategy for combining a reference world with generated or imported underground terrain. */
public interface Merger {

    void mergeLand(ChunkReader reader, ChunkData chunkData, ChunkReader cavesReader);

    void reInsertLiquids(WorldReader worldReader, ChunkData chunkData);

    boolean isUnderground(int globalX, int y, int globalZ);

    boolean shouldGenerateNoise();
}
