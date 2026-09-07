package com.kntrel.mc.underilla.core.cleanup;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Applies configured support and replacement rules to every block in a chunk. */
public final class BlockCleanupPatcher implements ChunkPatcher {

    private final BlockFactory blocks;
    private final Function<ID, Optional<ID>> supportReplacement;
    private final Function<ID, Optional<ID>> blockReplacement;

    public BlockCleanupPatcher(
            BlockFactory blocks,
            Function<ID, Optional<ID>> supportReplacement,
            Function<ID, Optional<ID>> blockReplacement
    ) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.supportReplacement = Objects.requireNonNull(supportReplacement, "supportReplacement");
        this.blockReplacement = Objects.requireNonNull(blockReplacement, "blockReplacement");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        for (int y = targetChunk.getMinHeight(); y < targetChunk.getMaxHeight(); y++) {
            for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
                for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                    cleanBlock(targetChunk, x, y, z);
                }
            }
        }
    }

    private void cleanBlock(ChunkData targetChunk, int x, int y, int z) {
        Block original = targetChunk.getBlock(x, y, z);
        ID originalID = original.id();

        if (   y > targetChunk.getMinHeight()
            && !original.isAir()
            && !targetChunk.getBlock(x, y - 1, z).isSolid()
        ) {
            replace(targetChunk, x, y, z, supportReplacement.apply(originalID));
        }

        replace(targetChunk, x, y, z, blockReplacement.apply(originalID));
    }

    private void replace(ChunkData targetChunk, int x, int y, int z, Optional<ID> replacement) {
        replacement.ifPresent(id -> targetChunk.setBlock(x, y, z, blocks.create(id)));
    }
}
