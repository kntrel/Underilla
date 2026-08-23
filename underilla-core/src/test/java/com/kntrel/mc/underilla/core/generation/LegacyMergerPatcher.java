package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;

/**
 * Presents the future patcher contract to characterization tests while adapting
 * it to the legacy merger API.
 */
final class LegacyMergerPatcher {

    private final Merger merger;
    private final WorldReader surfaceWorld;
    private final WorldReader cavesWorld;

    LegacyMergerPatcher(Merger merger, WorldReader surfaceWorld, WorldReader cavesWorld) {
        this.merger = Objects.requireNonNull(merger, "merger");
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.cavesWorld = cavesWorld;
    }

    void patch(ChunkData chunk) {
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();
        ChunkReader surfaceChunk = requiredChunk(surfaceWorld, chunkX, chunkZ, "surface");
        ChunkReader cavesChunk = cavesWorld == null ? null : requiredChunk(cavesWorld, chunkX, chunkZ, "caves");
        merger.mergeLand(surfaceChunk, chunk, cavesChunk);
    }

    private static ChunkReader requiredChunk(WorldReader world, int chunkX, int chunkZ, String worldName) {
        return world.readChunk(chunkX, chunkZ).orElseThrow(() -> new IllegalStateException(
                "Missing " + worldName + " chunk at " + chunkX + ", " + chunkZ));
    }
}
