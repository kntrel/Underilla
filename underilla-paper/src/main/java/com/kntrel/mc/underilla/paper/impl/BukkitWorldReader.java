package com.kntrel.mc.underilla.paper.impl;

import com.jkantrell.mca.Chunk;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.DiskWorldReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import java.io.File;
import java.util.List;

public class BukkitWorldReader extends DiskWorldReader {

    // CONSTRUCTORS
    public BukkitWorldReader(String regionPath, int regionCacheSize) throws NoSuchFieldException {
        super(regionPath, regionCacheSize);
    }
    public BukkitWorldReader(String regionPath, String entityRegionPath, int regionCacheSize) throws NoSuchFieldException {
        super(regionPath, entityRegionPath, regionCacheSize);
    }
    public BukkitWorldReader(File regionDirectory, int regionCacheSize) throws NoSuchFieldException {
        super(regionDirectory, regionCacheSize);
    }
    public BukkitWorldReader(File regionDirectory, File entityRegionDirectory, int regionCacheSize) throws NoSuchFieldException {
        super(regionDirectory, entityRegionDirectory, regionCacheSize);
    }


    // IMPLEMENTATIONS
    @Override
    protected ChunkReader newChunkReader(Chunk chunk, List<EntityView> entities) {
        return new BukkitChunkReader(chunk, entities);
    }
}
