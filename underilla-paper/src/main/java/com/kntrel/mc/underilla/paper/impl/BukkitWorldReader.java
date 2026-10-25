package com.kntrel.mc.underilla.paper.impl;

import com.jkantrell.mca.Chunk;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.DiskWorldReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import java.io.File;
import java.util.List;
import com.kntrel.mc.underilla.core.api.World;

public class BukkitWorldReader extends DiskWorldReader {

    // CONSTRUCTORS
    public BukkitWorldReader(String regionPath, int regionCacheSize) throws NoSuchFieldException {
        this(new File(regionPath), null, regionCacheSize);
    }
    public BukkitWorldReader(String regionPath, String entityRegionPath, int regionCacheSize) throws NoSuchFieldException {
        this(new File(regionPath), entityRegionPath == null ? null : new File(entityRegionPath), regionCacheSize);
    }
    public BukkitWorldReader(File regionDirectory, int regionCacheSize) throws NoSuchFieldException {
        this(regionDirectory, null, regionCacheSize);
    }
    public BukkitWorldReader(File regionDirectory, File entityRegionDirectory, int regionCacheSize) throws NoSuchFieldException {
        this(regionDirectory, entityRegionDirectory, regionCacheSize, null);
    }

    public BukkitWorldReader(File regionDirectory, File entityRegionDirectory, int regionCacheSize, World world) throws NoSuchFieldException {
        super(regionDirectory, entityRegionDirectory, regionCacheSize, world);
    }


    // IMPLEMENTATIONS
    @Override
    protected ChunkReader newChunkReader(Chunk chunk, List<EntityView> entities) {
        return new BukkitChunkReader(chunk, entities);
    }
}
