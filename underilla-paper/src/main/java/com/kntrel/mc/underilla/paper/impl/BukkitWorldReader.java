package com.kntrel.mc.underilla.paper.impl;

import com.jkantrell.mca.Chunk;
import com.kntrel.mc.underilla.core.api.GenerationLogger;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.DiskWorldReader;
import java.io.File;

public class BukkitWorldReader extends DiskWorldReader {

    // CONSTRUCTORS
    public BukkitWorldReader(String worldPath, int cacheSize, GenerationLogger logger) throws NoSuchFieldException {
        super(worldPath, cacheSize, logger);
    }
    public BukkitWorldReader(File worldDir, int cacheSize, GenerationLogger logger) throws NoSuchFieldException {
        super(worldDir, cacheSize, logger);
    }


    // IMPLEMENTATIONS
    @Override
    protected ChunkReader newChunkReader(Chunk chunk) { return new BukkitChunkReader(chunk); }
}
