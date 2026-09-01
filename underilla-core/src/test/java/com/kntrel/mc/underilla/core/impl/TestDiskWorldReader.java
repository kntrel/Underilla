package com.kntrel.mc.underilla.core.impl;

import com.jkantrell.mca.Chunk;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.DiskWorldReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import java.io.File;
import java.util.List;
import java.util.Objects;

/** Disk-backed world reader that converts Anvil data without Bukkit. */
public final class TestDiskWorldReader extends DiskWorldReader {

    private final TestBlockFactory blocks;

    public TestDiskWorldReader(File regionDirectory, int cacheSize, TestBlockFactory blocks)
            throws NoSuchFieldException {
        this(regionDirectory, null, cacheSize, blocks);
    }

    public TestDiskWorldReader(File regionDirectory, File entityRegionDirectory, int cacheSize, TestBlockFactory blocks)
            throws NoSuchFieldException {
        super(regionDirectory, entityRegionDirectory, cacheSize);
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    @Override
    protected ChunkReader newChunkReader(Chunk chunk, List<EntityView> entities) {
        return new TestChunkReader(chunk, entities, blocks);
    }
}
