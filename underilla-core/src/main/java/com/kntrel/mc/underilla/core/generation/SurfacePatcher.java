package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.Vector;
import com.kntrel.mc.underilla.core.vector.VectorIterable;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Copies reference terrain above the configured boundary. */
public final class SurfacePatcher implements ChunkPatcher {

    private final WorldReader surfaceWorld;
    private final Boundary boundary;
    private final int minimumY;
    private final Supplier<Block> air;
    private final Predicate<Block> keptSurfaceBlock;
    private final UnaryOperator<Block> surfaceBlockTransformer;

    public SurfacePatcher(
            WorldReader surfaceWorld,
            Boundary boundary,
            int minimumY,
            Supplier<Block> air,
            Predicate<Block> keptSurfaceBlock,
            UnaryOperator<Block> surfaceBlockTransformer
    ) {
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.minimumY = minimumY;
        this.air = Objects.requireNonNull(air, "air");
        this.keptSurfaceBlock = Objects.requireNonNull(keptSurfaceBlock, "keptSurfaceBlock");
        this.surfaceBlockTransformer = Objects.requireNonNull(surfaceBlockTransformer, "surfaceBlockTransformer");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        ChunkReader surfaceChunk = surfaceWorld.readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ()).orElse(null);
        if (surfaceChunk == null) {
            return;
        }

        int airColumn = surfaceChunk.airSectionsBottom();
        targetChunk.setRegion(0, airColumn, 0, GenerationConstants.CHUNK_SIZE, targetChunk.getMaxHeight(),
                GenerationConstants.CHUNK_SIZE, air.get());

        VectorIterable iterable = new VectorIterable(0, GenerationConstants.CHUNK_SIZE,
                minimumY, airColumn, 0, GenerationConstants.CHUNK_SIZE);
        int columnBoundary = minimumY;
        int lastX = -1;
        int lastZ = -1;
        for (Vector<Integer> vector : iterable) {
            Block referenceBlock = surfaceChunk.blockAt(vector.x(), vector.y(), vector.z()).orElseGet(air);
            referenceBlock = surfaceBlockTransformer.apply(referenceBlock);

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
        return keptSurfaceBlock.test(referenceBlock)
                && (undergroundBlock == null || undergroundBlock.isSolid());
    }
}
