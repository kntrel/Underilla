package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import java.util.Objects;

/** Applies an ordered set of block patchers during one traversal of a chunk. */
public final class PerBlockChunkPatcher implements Patcher<ChunkData> {

    private final Patcher<ChunkBlock>[] blockPatchers;

    /** Traverses the entire target chunk and writes candidates changed by the supplied patchers. */
    @SafeVarargs
    public PerBlockChunkPatcher(Patcher<ChunkBlock>... blockPatchers) {
        Objects.requireNonNull(blockPatchers, "blockPatchers");
        this.blockPatchers = blockPatchers.clone();
        for (Patcher<ChunkBlock> blockPatcher : this.blockPatchers) {
            Objects.requireNonNull(blockPatcher, "blockPatcher");
        }
    }

    @Override
    public void patch(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        for (int y = targetChunk.getMinHeight(); y < targetChunk.getMaxHeight(); y++) {
            for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
                for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                    Block currentBlock = targetChunk.getBlock(x, y, z);
                    ChunkBlock block = new ChunkBlock(targetChunk, x, y, z, currentBlock);
                    for (Patcher<ChunkBlock> blockPatcher : blockPatchers) {
                        blockPatcher.patch(block);
                    }
                    if (block.changed()) {
                        block.write();
                    }
                }
            }
        }
    }

}
