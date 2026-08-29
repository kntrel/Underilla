package com.kntrel.mc.underilla.core.impl;

import com.jkantrell.mca.Chunk;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.DiskWorldReader;
import java.io.File;
import java.util.Objects;

/** Disk-backed world reader that converts Anvil data without Bukkit. */
public final class TestDiskWorldReader extends DiskWorldReader {

    private final TestBlockFactory blocks;

    public TestDiskWorldReader(File regionDirectory, int cacheSize, TestBlockFactory blocks)
            throws NoSuchFieldException {
        super(regionDirectory, cacheSize);
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    @Override
    protected ChunkReader newChunkReader(Chunk chunk) {
        return new TestChunkReader(chunk, blocks);
    }
}
