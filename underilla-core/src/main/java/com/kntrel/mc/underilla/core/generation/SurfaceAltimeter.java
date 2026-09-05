package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.HeightMapType;
import com.kntrel.mc.underilla.core.api.WorldInfo;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Resolves base heights from the reference surface world. */
public final class SurfaceAltimeter implements Altimeter {

    private final WorldReader surfaceWorld;
    private final Supplier<Block> air;

    public SurfaceAltimeter(WorldReader surfaceWorld, Supplier<Block> air) {
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.air = Objects.requireNonNull(air, "air");
    }

    @Override
    public int heightAt(WorldInfo worldInfo, int x, int z, HeightMapType heightMap) {
        int chunkX = Math.floorDiv(x, GenerationConstants.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, GenerationConstants.CHUNK_SIZE);
        ChunkReader surfaceChunk = surfaceWorld.readChunk(chunkX, chunkZ).orElse(null);
        if (surfaceChunk == null) {
            return 0;
        }

        Predicate<Block> isIgnored = switch (heightMap) {
            case WORLD_SURFACE, WORLD_SURFACE_WG -> Block::isAir;
            case OCEAN_FLOOR, OCEAN_FLOOR_WG, MOTION_BLOCKING -> block -> !block.isSolid();
            case MOTION_BLOCKING_NO_LEAVES -> block -> !block.isSolid()
                    || block.getName().toLowerCase().contains("leaves");
        };

        int y = surfaceChunk.airSectionsBottom();
        Block block;
        do {
            y--;
            if (y < worldInfo.getMinHeight()) {
                break;
            }
            block = surfaceChunk.blockAt(
                    Math.floorMod(x, GenerationConstants.CHUNK_SIZE),
                    y,
                    Math.floorMod(z, GenerationConstants.CHUNK_SIZE)
            ).orElseGet(air);
        } while (isIgnored.test(block));
        return y + 1;
    }
}
