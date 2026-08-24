package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.Vector;
import com.kntrel.mc.underilla.core.vector.VectorIterable;
import java.util.Objects;

/** Copies reference terrain above the configured boundary. */
public final class SurfacePatcher implements Patcher {

    private final WorldReader surfaceWorld;
    private final Boundary boundary;
    private final GenerationContext context;

    public SurfacePatcher(WorldReader surfaceWorld, Boundary boundary, GenerationContext context) {
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        ChunkReader surfaceChunk = surfaceWorld.readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ()).orElse(null);
        if (surfaceChunk == null) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int airColumn = surfaceChunk.airSectionsBottom();
        targetChunk.setRegion(0, airColumn, 0, GenerationConstants.CHUNK_SIZE, targetChunk.getMaxHeight(),
                GenerationConstants.CHUNK_SIZE, context.blocks().air());

        VectorIterable iterable = new VectorIterable(0, GenerationConstants.CHUNK_SIZE,
                context.config().generationAreaMinY(), airColumn, 0, GenerationConstants.CHUNK_SIZE);
        int columnBoundary = context.config().maxHeightOfCaves();
        int lastX = -1;
        int lastZ = -1;
        Generator.addTime("Create VectorIterable to patch land", startTime);
        for (Vector<Integer> vector : iterable) {
            startTime = System.currentTimeMillis();
            Block referenceBlock = surfaceChunk.blockAt(vector.x(), vector.y(), vector.z()).orElse(context.blocks().air());
            referenceBlock = replaceSurfaceBlockIfNecessary(referenceBlock);
            Generator.addTime("Read block data from reference world", startTime);

            startTime = System.currentTimeMillis();
            Block undergroundBlock = targetChunk.getBlock(vector);
            Generator.addTime("Read block data from underground world", startTime);

            startTime = System.currentTimeMillis();
            if (vector.x() != lastX || vector.z() != lastZ) {
                lastX = vector.x();
                lastZ = vector.z();
                columnBoundary = boundary.at(surfaceChunk.getGlobalX(vector.x()), surfaceChunk.getGlobalZ(vector.z()));
            }
            Generator.addTime("Calculate patch boundary", startTime);

            startTime = System.currentTimeMillis();
            if (vector.y() > columnBoundary
                    || shouldKeepReferenceBlockInUnderground(referenceBlock, undergroundBlock)) {
                targetChunk.setBlock(vector, referenceBlock);
            }
            Generator.addTime("Patch reference block or not", startTime);
        }
    }

    private boolean shouldKeepReferenceBlockInUnderground(Block referenceBlock, Block undergroundBlock) {
        return context.config().shouldKeepSurfaceBlockInCaves(referenceBlock.getName())
                && (undergroundBlock == null || undergroundBlock.isSolid());
    }

    private Block replaceSurfaceBlockIfNecessary(Block block) {
        String replacement = context.config().surfaceBlockReplacement(block.getName());
        return replacement == null ? block : context.blocks().create(replacement);
    }
}
