package com.kntrel.mc.underilla.core;

import com.jkantrell.mca.MCAUtil;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.HeightMapType;
import com.kntrel.mc.underilla.core.api.WorldInfo;
import com.kntrel.mc.underilla.core.generation.GenerationContext;
import com.kntrel.mc.underilla.core.generation.GenerationFlags;
import com.kntrel.mc.underilla.core.generation.Patcher;
import com.kntrel.mc.underilla.core.generation.PatchingPlan;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.profiling.Tracker;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class UnderillaEngine {

    private final WorldReader worldSurfaceReader;
    private final PatchingPlan patchingPlan;
    private final GenerationContext context;
    private final Tracker tracker;
    private final List<InstrumentedPatcher> terrainPatchers;
    private final InstrumentedPatcher liquidPatcher;

    public UnderillaEngine(WorldReader worldSurfaceReader, PatchingPlan patchingPlan, GenerationContext context,
            Instrumenter instrumenter) {
        this.worldSurfaceReader = Objects.requireNonNull(worldSurfaceReader, "worldSurfaceReader");
        this.patchingPlan = Objects.requireNonNull(patchingPlan, "patchingPlan");
        this.context = Objects.requireNonNull(context, "context");
        Objects.requireNonNull(instrumenter, "instrumenter");
        this.tracker = instrumenter.tracker(UnderillaEngine.class);
        this.terrainPatchers = patchingPlan.terrainPatchers().stream()
                .map(patcher -> new InstrumentedPatcher(patcher, instrumenter.tracker(patcher.getClass())))
                .toList();
        this.liquidPatcher = new InstrumentedPatcher(
                patchingPlan.liquidPatcher(),
                instrumenter.tracker(patchingPlan.liquidPatcher().getClass()));
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

    /**
     * Applies the terrain patching pipeline when the reference world contains the target chunk.
     *
     * @return {@code true} when the pipeline ran, or {@code false} when the reference chunk was absent
     */
    public boolean tryPatchTerrain(ChunkData chunkData) {
        if (this.worldSurfaceReader.readChunk(chunkData.getChunkX(), chunkData.getChunkZ()).isEmpty()) {
            return false;
        }

        try (var _ = tracker.stopwatch("terrain_patch")) {
            for (InstrumentedPatcher patcher : terrainPatchers) {
                patcher.patch(chunkData);
            }
        }
        return true;
    }

    /**
     * Applies the liquid patching pipeline when the reference world contains the target chunk.
     *
     * @return {@code true} when the pipeline ran, or {@code false} when the reference chunk was absent
     */
    public boolean tryPatchLiquids(ChunkData chunkData) {
        if (this.worldSurfaceReader.readChunk(chunkData.getChunkX(), chunkData.getChunkZ()).isEmpty()) {
            return false;
        }

        try (var _ = tracker.stopwatch("liquid_patch")) {
            liquidPatcher.patch(chunkData);
        }
        return true;
    }

    /**
     * Selects a biome using the reference-world policy when the reference contains the requested position.
     * The biome already stored in {@code biomeData} is preserved when it satisfies the preservation policy.
     *
     * @return {@code true} when the engine handled the position, or {@code false} when it is outside the reference
     */
    public boolean tryPatchBiome(BiomeData biomeData) {
        Objects.requireNonNull(biomeData, "biomeData");

        if (!context.config().isInsideGenerationArea(biomeData.getX(), biomeData.getZ())) {
            return false;
        }

        try (var _ = tracker.stopwatch("patch_biome")) {
            int referenceY = context.config().surfaceBiomeUseTopYOnly()
                    ? context.config().generationAreaMaxY()
                    : biomeData.getY();
            Biome referenceBiome = worldSurfaceReader.biomeAt(biomeData.getX(), referenceY, biomeData.getZ()).orElse(null);
            if (referenceBiome == null) {
                return false;
            }

            if (!context.config().isSurfaceWorldOnlyBiome(referenceBiome.getName())
                    && context.config().shouldPreserveBiome(biomeData.get().getName())
                    && isBiomeUnderSurface(biomeData)) {
                return true;
            }

            biomeData.set(referenceBiome);
            return true;
        }
    }

    private boolean isBiomeUnderSurface(BiomeData biomeData) {
        if (!context.config().preserveBiomesOnlyUnderSurface()) {
            return true;
        }

        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        int cellX = biomeData.getBiomeX() * cellSize;
        int cellZ = biomeData.getBiomeZ() * cellSize;
        for (int x = cellX; x < cellX + cellSize; x++) {
            for (int z = cellZ; z < cellZ + cellSize; z++) {
                if (!patchingPlan.boundary().isBelowEquals(x, biomeData.getY(), z)) {
                    return false;
                }
            }
        }
        return true;
    }

    public GenerationFlags getFlags() {
        return new GenerationFlags(
                patchingPlan.generateNoise(),
                true,
                context.config().carversEnabled(),
                context.config().vanillaPopulationEnabled(),
                true,
                context.config().structuresEnabled()
        );
    }

    private record InstrumentedPatcher(Patcher patcher, Tracker tracker) {

        private InstrumentedPatcher {
            Objects.requireNonNull(patcher, "patcher");
            Objects.requireNonNull(tracker, "tracker");
        }

        private void patch(ChunkData chunkData) {
            try (var _ = tracker.stopwatch("patch")) {
                patcher.patch(chunkData);
            }
        }
    }
}
