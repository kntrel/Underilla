package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;

/** Copies underground blocks from a caves world into the target chunk. */
public final class CavePatcher implements ChunkPatcher {

    private final WorldReader cavesWorld;
    private final Boundary boundary;
    private final GenerationConfig config;
    private final BlockFactory blocks;

    public CavePatcher(WorldReader cavesWorld, Boundary boundary, GenerationConfig config, BlockFactory blocks) {
        this.cavesWorld = Objects.requireNonNull(cavesWorld, "cavesWorld");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.config = Objects.requireNonNull(config, "config");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        ChunkReader cavesChunk = cavesWorld.readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ()).orElse(null);
        if (cavesChunk == null) {
            return;
        }

        int minimumY = Math.max(config.generationAreaMinY(), targetChunk.getMinHeight());
        int maximumY = targetChunk.getMaxHeight() - 1;
        int chunkOriginX = targetChunk.getChunkX() * GenerationConstants.CHUNK_SIZE;
        int chunkOriginZ = targetChunk.getChunkZ() * GenerationConstants.CHUNK_SIZE;
        for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                int columnMaximumY = Math.min(maximumY, boundary.at(chunkOriginX + x, chunkOriginZ + z));
                for (int y = minimumY; y <= columnMaximumY; y++) {
                    Block caveBlock = cavesChunk.blockAt(x, y, z).orElse(blocks.air());
                    targetChunk.setBlock(x, y, z, caveBlock);
                }
            }
        }
    }
}
