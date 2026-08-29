package com.kntrel.mc.underilla.paper.impl;

import com.jkantrell.mca.Chunk;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.DiskWorldReader;
import java.io.File;

public class BukkitWorldReader extends DiskWorldReader {

    // CONSTRUCTORS
    public BukkitWorldReader(String regionPath, int cacheSize) throws NoSuchFieldException {
        super(regionPath, cacheSize);
    }
    public BukkitWorldReader(File regionDirectory, int cacheSize) throws NoSuchFieldException {
        super(regionDirectory, cacheSize);
    }


    // IMPLEMENTATIONS
    @Override
    protected ChunkReader newChunkReader(Chunk chunk) { return new BukkitChunkReader(chunk); }
}
