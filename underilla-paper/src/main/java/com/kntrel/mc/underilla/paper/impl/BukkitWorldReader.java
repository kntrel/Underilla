package com.kntrel.mc.underilla.paper.impl;

import com.jkantrell.mca.Chunk;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.DiskWorldReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import java.io.File;
import java.util.List;

public class BukkitWorldReader extends DiskWorldReader {

    // CONSTRUCTORS
    public BukkitWorldReader(String regionPath, int cacheSize) throws NoSuchFieldException {
        super(regionPath, cacheSize);
    }
    public BukkitWorldReader(String regionPath, String entityRegionPath, int cacheSize) throws NoSuchFieldException {
        super(regionPath, entityRegionPath, cacheSize);
    }
    public BukkitWorldReader(File regionDirectory, int cacheSize) throws NoSuchFieldException {
        super(regionDirectory, cacheSize);
    }
    public BukkitWorldReader(File regionDirectory, File entityRegionDirectory, int cacheSize) throws NoSuchFieldException {
        super(regionDirectory, entityRegionDirectory, cacheSize);
    }


    // IMPLEMENTATIONS
    @Override
    protected ChunkReader newChunkReader(Chunk chunk, List<EntityView> entities) {
        return new BukkitChunkReader(chunk, entities);
    }
}
