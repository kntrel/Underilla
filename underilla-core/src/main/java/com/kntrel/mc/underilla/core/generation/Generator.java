package com.kntrel.mc.underilla.core.generation;

import com.jkantrell.mca.MCAUtil;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.HeightMapType;
import com.kntrel.mc.underilla.core.api.WorldInfo;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class Generator {

    private final WorldReader worldSurfaceReader;
    private final Merger merger;
    private final GenerationContext context;
    public static final Map<String, Long> times = new ConcurrentHashMap<>();

    public Generator(WorldReader worldSurfaceReader, Merger merger, GenerationContext context) {
        this.worldSurfaceReader = Objects.requireNonNull(worldSurfaceReader, "worldSurfaceReader");
        this.merger = Objects.requireNonNull(merger, "merger");
        this.context = Objects.requireNonNull(context, "context");
    }

    // TODO fix issue with short grass making village houses 1 block higher.
    public int getBaseHeight(WorldInfo worldInfo, int x, int z, HeightMapType heightMap) {
        int chunkX = MCAUtil.blockToChunk(x), chunkZ = MCAUtil.blockToChunk(z);
        ChunkReader chunkReader = this.worldSurfaceReader.readChunk(chunkX, chunkZ).orElse(null);
        if (chunkReader == null) {
            return 0;
        }

        Predicate<Block> check = switch (heightMap) {
            case WORLD_SURFACE, WORLD_SURFACE_WG -> Block::isAir;
            case OCEAN_FLOOR, OCEAN_FLOOR_WG, MOTION_BLOCKING -> block -> !block.isSolid();
            case MOTION_BLOCKING_NO_LEAVES -> block -> !block.isSolid() || block.getName().toLowerCase().contains("leaves");
        };
        int y = chunkReader.airSectionsBottom();
        Block block;
        do {
            y--;
            if (y < worldInfo.getMinHeight()) {
                break;
            }
            block = chunkReader.blockAt(Math.floorMod(x, GenerationConstants.CHUNK_SIZE), y,
                    Math.floorMod(z, GenerationConstants.CHUNK_SIZE)).orElse(context.blocks().air());
        } while (check.test(block));
        return y + 1;
    }

    public void generateSurface(ChunkReader reader, ChunkData chunkData, ChunkReader cavesReader) {
        this.merger.mergeLand(reader, chunkData, cavesReader);
    }

    public void reInsertLiquidsOverWorldSurface(WorldReader worldReader, ChunkData chunkData) {
        merger.reInsertLiquids(worldReader, chunkData);
    }

    public boolean shouldGenerateNoise(int chunkX, int chunkZ) {
        return merger.shouldGenerateNoise();
    }

    public boolean shouldGenerateSurface(int chunkX, int chunkZ) {
        return true;
    }

    public boolean shouldGenerateCaves(int chunkX, int chunkZ) {
        return context.config().carversEnabled();
    }

    public boolean shouldGenerateDecorations(int chunkX, int chunkZ) {
        return context.config().vanillaPopulationEnabled();
    }

    public boolean shouldGenerateMobs(int chunkX, int chunkZ) { return true; }

    public boolean shouldGenerateStructures(int chunkX, int chunkZ) {
        return context.config().structuresEnabled();
    }

    public static void addTime(String name, long startTime) {
        times.merge(name, System.currentTimeMillis() - startTime, Long::sum);
    }
}
