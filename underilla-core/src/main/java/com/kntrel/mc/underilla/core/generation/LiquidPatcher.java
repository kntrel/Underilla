package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.LocatedBlock;
import java.util.List;
import java.util.Objects;

/** Restores reference-world liquids above the terrain boundary after carvers run. */
public final class LiquidPatcher implements Patcher {

    private final WorldReader surfaceWorld;
    private final Boundary boundary;
    private final GenerationContext context;

    public LiquidPatcher(WorldReader surfaceWorld, Boundary boundary, GenerationContext context) {
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        ChunkReader surfaceChunk = surfaceWorld.readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ()).orElse(null);
        if (surfaceChunk == null) {
            context.logger().warning(String.format("No reader found for chunk %d, %d. Skipping liquid reinsertion.",
                    targetChunk.getChunkX(), targetChunk.getChunkZ()));
            return;
        }

        List<LocatedBlock> locations = surfaceChunk.locationsOf(Block::isLiquid).stream()
                .filter(location -> boundary.isAbove(
                        targetChunk.getChunkX() * GenerationConstants.CHUNK_SIZE + location.x(),
                        location.y(),
                        targetChunk.getChunkZ() * GenerationConstants.CHUNK_SIZE + location.z()))
                .toList();

        locations.forEach(location -> {
            Block block = targetChunk.getBlock(location.vector());
            block.waterlog();
            targetChunk.setBlock(location.vector(), block);
        });
    }
}
