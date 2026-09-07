package com.kntrel.mc.underilla.core.cleanup;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.vector.Vector;
import com.kntrel.mc.underilla.core.vector.VectorIterable;
import java.util.Objects;
import java.util.function.Function;

/** Applies configured support and replacement rules to every block in a chunk. */
public final class BlockCleanupPatcher implements ChunkPatcher {

    private final BlockFactory blocks;
    private final Function<String, String> supportReplacement;
    private final Function<String, String> blockReplacement;

    public BlockCleanupPatcher(
            BlockFactory blocks,
            Function<String, String> supportReplacement,
            Function<String, String> blockReplacement
    ) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.supportReplacement = Objects.requireNonNull(supportReplacement, "supportReplacement");
        this.blockReplacement = Objects.requireNonNull(blockReplacement, "blockReplacement");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        VectorIterable blocks = new VectorIterable(
                0, GenerationConstants.CHUNK_SIZE,
                targetChunk.getMinHeight(), targetChunk.getMaxHeight(),
                0, GenerationConstants.CHUNK_SIZE
        );
        for (Vector<Integer> pos : blocks) {
            cleanBlock(targetChunk, pos.x(), pos.y(), pos.z());
        }
    }

    private void cleanBlock(ChunkData targetChunk, int x, int y, int z) {
        Block original = targetChunk.getBlock(x, y, z);
        String originalName = original.getName();

        if (   y > targetChunk.getMinHeight()
            && !original.isAir()
            && !targetChunk.getBlock(x, y - 1, z).isSolid()
        ) {
            replace(targetChunk, x, y, z, supportReplacement.apply(originalName));
        }

        replace(targetChunk, x, y, z, blockReplacement.apply(originalName));
    }

    private void replace(ChunkData targetChunk, int x, int y, int z, String replacement) {
        if (replacement == null) { return; }
        targetChunk.setBlock(x, y, z, blocks.create(replacement));
    }
}
