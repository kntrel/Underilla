package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.Vector;
import com.kntrel.mc.underilla.core.vector.VectorIterable;
import java.util.Objects;

/** Copies reference terrain above the configured boundary. */
public final class SurfacePatcher implements ChunkPatcher {

    private final WorldReader surfaceWorld;
    private final Boundary boundary;
    private final GenerationConfig config;
    private final BlockFactory blocks;

    public SurfacePatcher(WorldReader surfaceWorld, Boundary boundary, GenerationConfig config, BlockFactory blocks) {
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.config = Objects.requireNonNull(config, "config");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        ChunkReader surfaceChunk = surfaceWorld.readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ()).orElse(null);
        if (surfaceChunk == null) {
            return;
        }

        int airColumn = surfaceChunk.airSectionsBottom();
        targetChunk.setRegion(0, airColumn, 0, GenerationConstants.CHUNK_SIZE, targetChunk.getMaxHeight(),
                GenerationConstants.CHUNK_SIZE, blocks.air());

        VectorIterable iterable = new VectorIterable(0, GenerationConstants.CHUNK_SIZE,
                config.generationAreaMinY(), airColumn, 0, GenerationConstants.CHUNK_SIZE);
        int columnBoundary = config.maxHeightOfCaves();
        int lastX = -1;
        int lastZ = -1;
        for (Vector<Integer> vector : iterable) {
            Block referenceBlock = surfaceChunk.blockAt(vector.x(), vector.y(), vector.z()).orElse(blocks.air());
            referenceBlock = replaceSurfaceBlockIfNecessary(referenceBlock);

            Block undergroundBlock = targetChunk.getBlock(vector);

            if (vector.x() != lastX || vector.z() != lastZ) {
                lastX = vector.x();
                lastZ = vector.z();
                columnBoundary = boundary.at(surfaceChunk.getGlobalX(vector.x()), surfaceChunk.getGlobalZ(vector.z()));
            }

            if (vector.y() > columnBoundary
                    || shouldKeepReferenceBlockInUnderground(referenceBlock, undergroundBlock)) {
                targetChunk.setBlock(vector, referenceBlock);
            }
        }
    }

    private boolean shouldKeepReferenceBlockInUnderground(Block referenceBlock, Block undergroundBlock) {
        return config.shouldKeepSurfaceBlockInCaves(referenceBlock.getName())
                && (undergroundBlock == null || undergroundBlock.isSolid());
    }

    private Block replaceSurfaceBlockIfNecessary(Block block) {
        String replacement = config.surfaceBlockReplacement(block.getName());
        return replacement == null ? block : blocks.create(replacement);
    }
}
