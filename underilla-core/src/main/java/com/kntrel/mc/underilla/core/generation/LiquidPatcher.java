package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.LocatedBlock;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Restores reference-world liquids inside the world mask after carvers run. */
public final class LiquidPatcher implements ChunkPatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiquidPatcher.class);
    private final WorldReader surfaceWorld;
    private final WorldMask worldMask;

    public LiquidPatcher(WorldReader surfaceWorld, WorldMask worldMask) {
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.worldMask = Objects.requireNonNull(worldMask, "worldMask");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        ChunkReader surfaceChunk = surfaceWorld.readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ()).orElse(null);
        if (surfaceChunk == null) {
            LOGGER.warn("No reader found for chunk {}, {}. Skipping liquid reinsertion.",
                    targetChunk.getChunkX(), targetChunk.getChunkZ());
            return;
        }

        List<LocatedBlock> locations = surfaceChunk.locationsOf(Block::isLiquid).stream()
                .filter(location -> worldMask.contains(
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
