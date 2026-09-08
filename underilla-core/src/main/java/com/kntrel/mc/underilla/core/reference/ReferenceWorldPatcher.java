package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.generation.WorldMask;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.Vector;
import com.kntrel.mc.underilla.core.vector.VectorIterable;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Copies reference-world blocks selected by a world mask. */
public final class ReferenceWorldPatcher implements ChunkPatcher {

    private final WorldReader referenceWorld;
    private final WorldMask worldMask;
    private final int minimumY;
    private final Supplier<Block> air;
    private final Predicate<Block> keptSurfaceBlock;
    private final UnaryOperator<Block> surfaceBlockTransformer;

    public ReferenceWorldPatcher(
            WorldReader referenceWorld,
            WorldMask worldMask,
            int minimumY,
            Supplier<Block> air,
            Predicate<Block> keptSurfaceBlock,
            UnaryOperator<Block> surfaceBlockTransformer
    ) {
        this.referenceWorld = Objects.requireNonNull(referenceWorld, "referenceWorld");
        this.worldMask = Objects.requireNonNull(worldMask, "worldMask");
        this.minimumY = minimumY;
        this.air = Objects.requireNonNull(air, "air");
        this.keptSurfaceBlock = Objects.requireNonNull(keptSurfaceBlock, "keptSurfaceBlock");
        this.surfaceBlockTransformer = Objects.requireNonNull(surfaceBlockTransformer, "surfaceBlockTransformer");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        ChunkReader referenceChunk = referenceWorld.readChunk(
                targetChunk.getChunkX(),
                targetChunk.getChunkZ()
        ).orElse(null);
        if (referenceChunk == null) { return; }

        VectorIterable iterable = new VectorIterable(
                0, GenerationConstants.CHUNK_SIZE,
                Math.max(minimumY, targetChunk.getMinHeight()), targetChunk.getMaxHeight(),
                0, GenerationConstants.CHUNK_SIZE
        );
        for (Vector<Integer> vector : iterable) {
            Block referenceBlock = referenceChunk.blockAt(vector.x(), vector.y(), vector.z()).orElseGet(air);
            referenceBlock = surfaceBlockTransformer.apply(referenceBlock);

            Block undergroundBlock = targetChunk.getBlock(vector);

            if (    worldMask.contains(
                        referenceChunk.getGlobalX(vector.x()),
                        vector.y(),
                        referenceChunk.getGlobalZ(vector.z())
                    )
                 || shouldKeepReferenceBlockInUnderground(referenceBlock, undergroundBlock)
            ) {
                targetChunk.setBlock(vector, referenceBlock);
            }
        }
    }

    private boolean shouldKeepReferenceBlockInUnderground(Block referenceBlock, Block undergroundBlock) {
        return     keptSurfaceBlock.test(referenceBlock)
                && (undergroundBlock == null || undergroundBlock.isSolid());
    }
}
