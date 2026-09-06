package com.kntrel.mc.underilla.paper.generation;

import com.kntrel.mc.underilla.core.api.HeightMapType;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.paper.Underilla;
import com.kntrel.mc.underilla.paper.cleaning.CleanBlocks;
import com.kntrel.mc.underilla.paper.impl.BukkitChunkData;
import com.kntrel.mc.underilla.paper.impl.BukkitLoadedChunkData;
import com.kntrel.mc.underilla.paper.impl.BukkitRegionChunkData;
import com.kntrel.mc.underilla.paper.impl.BukkitWorldInfo;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.IntegerKeys;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.SetBiomeStringKeys;
import com.kntrel.mc.underilla.paper.profiling.ChunkGenerationProfiler;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.jetbrains.annotations.NotNull;

public class UnderillaChunkGenerator extends ChunkGenerator implements Listener {
    // TODO : For performance reason, we should generate and empty world if transfer_world_from_caves_world==true

    // ASSETS
    private static final Map<HeightMap, HeightMapType> HEIGHTMAPS_MAP = Map.of(
            HeightMap.OCEAN_FLOOR,               HeightMapType.OCEAN_FLOOR,
            HeightMap.OCEAN_FLOOR_WG,            HeightMapType.OCEAN_FLOOR_WG,
            HeightMap.MOTION_BLOCKING,           HeightMapType.MOTION_BLOCKING,
            HeightMap.MOTION_BLOCKING_NO_LEAVES, HeightMapType.MOTION_BLOCKING_NO_LEAVES,
            HeightMap.WORLD_SURFACE,             HeightMapType.WORLD_SURFACE,
            HeightMap.WORLD_SURFACE_WG,          HeightMapType.WORLD_SURFACE_WG
    );


    // FIELDS
    private final WorldGenerationPlan generationPlan;
    private final ChunkGenerationProfiler chunkProfiler;
    private final String worldName;
    private volatile UnderillaBiomeProvider biomeProvider;
    private final @Nullable ChunkGenerator outOfTheSurfaceWorldGenerator;


    // CONSTRUCTORS
    public UnderillaChunkGenerator(
            WorldGenerationPlan generationPlan,
            @Nullable ChunkGenerator outOfTheSurfaceWorldGenerator,
            ChunkGenerationProfiler chunkProfiler,
            String worldName
    ) {
        this.generationPlan = generationPlan;
        this.outOfTheSurfaceWorldGenerator = outOfTheSurfaceWorldGenerator;
        this.chunkProfiler = chunkProfiler;
        this.worldName = worldName;
    }


    // IMPLEMENTATIONS
    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !event.getWorld().getName().equals(worldName)) {
            return;
        }
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        try {
            this.chunkProfiler.run(event.getWorld().getUID(), chunkX, chunkZ,
                    () -> generationPlan.tryAfterLoad(new BukkitLoadedChunkData(event.getChunk())));
        } finally {
            this.chunkProfiler.complete(event.getWorld().getUID(), chunkX, chunkZ);
        }
    }

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        // Do not use base height from VoidWorldGenerator if it is outside of the surface world, else it broke structures generation.
        // We only use UnderillaChunkGenerator base height to avoid a bug with the structure generation height.
        BukkitWorldInfo info = new BukkitWorldInfo(worldInfo);
        return this.generationPlan.altimeter().heightAt(info, x, z, HEIGHTMAPS_MAP.get(heightMap));
    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        chunkProfiler.run(worldInfo.getUID(), chunkX, chunkZ, () -> {
            if (!generationPlan.tryAfterNoise(new BukkitChunkData(chunkData, chunkX, chunkZ)) && outOfTheSurfaceWorldGenerator != null) {
                outOfTheSurfaceWorldGenerator.generateNoise(worldInfo, random, chunkX, chunkZ, chunkData);
            }
        });
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        chunkProfiler.run(worldInfo.getUID(), chunkX, chunkZ, () -> {
                if (!generationPlan.tryAfterSurface(new BukkitChunkData(chunkData, chunkX, chunkZ)) && outOfTheSurfaceWorldGenerator != null) {
                    outOfTheSurfaceWorldGenerator.generateSurface(worldInfo, random, chunkX, chunkZ, chunkData);
                }
            }
        );
    }

    @Override
    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        chunkProfiler.run(worldInfo.getUID(), chunkX, chunkZ, () -> {
                if (!generationPlan.tryAfterCarvers(new BukkitChunkData(chunkData, chunkX, chunkZ)) && outOfTheSurfaceWorldGenerator != null) {
                    outOfTheSurfaceWorldGenerator.generateCaves(worldInfo, random, chunkX, chunkZ, chunkData);
                }
            }
        );
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new Populator(this.generationPlan, this.chunkProfiler));
    }

    @Override
    public boolean shouldGenerateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (usesFallback(chunkX, chunkZ)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateNoise(worldInfo, random, chunkX, chunkZ);
        }

        return this.generationPlan.flags().noise();
    }

    @Override
    public boolean shouldGenerateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (usesFallback(chunkX, chunkZ)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateSurface(worldInfo, random, chunkX, chunkZ);
        }

        return this.generationPlan.flags().surface();
    }

    @Override
    public boolean shouldGenerateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (usesFallback(chunkX, chunkZ)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateCaves(worldInfo, random, chunkX, chunkZ);
        }

        return this.generationPlan.flags().carvers();
    }

    @Override
    public boolean shouldGenerateDecorations(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (usesFallback(chunkX, chunkZ)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateDecorations(worldInfo, random, chunkX, chunkZ);
        }

        return this.generationPlan.flags().features();
    }

    @Override
    public boolean shouldGenerateMobs(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (usesFallback(chunkX, chunkZ)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateMobs(worldInfo, random, chunkX, chunkZ);
        }

        return this.generationPlan.flags().mobs();
    }

    @Override
    public boolean shouldGenerateStructures(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (usesFallback(chunkX, chunkZ)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateStructures(worldInfo, random, chunkX, chunkZ);
        }

        return this.generationPlan.flags().structures();
    }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        int x = Math.min(Math.max(0, Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MIN_X)),
                Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MAX_X));
        int z = Math.min(Math.max(0, Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MIN_Z)),
                Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MAX_Z));
        return new Location(world, x, 100, z);
    }

    @Override
    public synchronized BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        if (biomeProvider == null) {
            BiomeProvider outsideSurfaceWorldBiomeProvider = outOfTheSurfaceWorldGenerator == null ? null
                    : outOfTheSurfaceWorldGenerator.getDefaultBiomeProvider(worldInfo);
            biomeProvider = new UnderillaBiomeProvider(generationPlan, outsideSurfaceWorldBiomeProvider, chunkProfiler);
        }
        return biomeProvider;
    }

    public Map<String, Long> getBiomesPlaced() {
        UnderillaBiomeProvider currentBiomeProvider = biomeProvider;
        return currentBiomeProvider == null ? Map.of() : currentBiomeProvider.getBiomesPlaced();
    }


    //HELPERS
    private boolean usesFallback(int chunkX, int chunkZ) {
        return outOfTheSurfaceWorldGenerator != null && !generationPlan.coverage().covers(chunkX, chunkZ);
    }


    // INNER CLASSES
    private static class Populator extends BlockPopulator {

        // FIELDS
        private final WorldGenerationPlan generationPlan;
        private final ChunkGenerationProfiler chunkProfiler;


        // CONSTRUCTORS
        public Populator(WorldGenerationPlan generationPlan, ChunkGenerationProfiler chunkProfiler) {
            this.generationPlan = generationPlan;
            this.chunkProfiler = chunkProfiler;
        }


        // IMPLEMENTATION
        @Override
        public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
            chunkProfiler.run(worldInfo.getUID(), chunkX, chunkZ,
                    () -> populateStep(worldInfo, chunkX, chunkZ, limitedRegion));
        }


        //HELPERS
        private void populateStep(WorldInfo worldInfo, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
            BukkitRegionChunkData chunkData = new BukkitRegionChunkData(
                    limitedRegion, chunkX, chunkZ, worldInfo.getMinHeight(), worldInfo.getMaxHeight());

            // The block populators are called after addVanillaDecorations(...) before light and mod spawn.
            // It is the final generation-time opportunity to clean decorated blocks and add reference entities.
            // Calling it here is thread-safe and lag-safe because Chunky waits for generation to finish before starting more chunks.
            if (Underilla.getUnderillaConfig().getBoolean(UnderillaConfig.BooleanKeys.CLEAN_BLOCKS_ENABLED)) {
                CleanBlocks.cleanBlocks(worldInfo, chunkX, chunkZ, limitedRegion);
            }
            this.generationPlan.tryAfterFeatures(chunkData);
        }
    }

}
