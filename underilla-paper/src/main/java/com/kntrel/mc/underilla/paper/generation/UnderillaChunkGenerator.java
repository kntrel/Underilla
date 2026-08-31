package com.kntrel.mc.underilla.paper.generation;

import com.kntrel.mc.underilla.core.api.HeightMapType;
import com.kntrel.mc.underilla.core.generation.GenerationContext;
import com.kntrel.mc.underilla.core.generation.PatchingPlan;
import com.kntrel.mc.underilla.core.UnderillaEngine;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.paper.Underilla;
import com.kntrel.mc.underilla.paper.cleaning.CleanBlocks;
import com.kntrel.mc.underilla.paper.impl.BukkitChunkData;
import com.kntrel.mc.underilla.paper.impl.BukkitRegionChunkData;
import com.kntrel.mc.underilla.paper.impl.BukkitWorldInfo;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.BooleanKeys;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.IntegerKeys;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.SetBiomeStringKeys;
import com.kntrel.mc.underilla.paper.profiling.ChunkGenerationProfiler;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nonnull;
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
import org.jetbrains.annotations.NotNull;

public class UnderillaChunkGenerator extends ChunkGenerator {
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
    private final UnderillaEngine engine;
    private final ChunkGenerationProfiler chunkProfiler;
    private volatile UnderillaBiomeProvider biomeProvider;
    private final @Nullable ChunkGenerator outOfTheSurfaceWorldGenerator;


    // CONSTRUCTORS
    public UnderillaChunkGenerator(
            @Nonnull WorldReader worldSurfaceReader,
            @Nullable ChunkGenerator outOfTheSurfaceWorldGenerator,
            PatchingPlan patchingPlan,
            GenerationContext generationContext,
            Instrumenter instrumenter,
            ChunkGenerationProfiler chunkProfiler
    ) {
        this.outOfTheSurfaceWorldGenerator = outOfTheSurfaceWorldGenerator;
        this.engine = new UnderillaEngine(worldSurfaceReader, patchingPlan, generationContext, instrumenter);
        this.chunkProfiler = chunkProfiler;
    }


    // IMPLEMENTATIONS
    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        // Do not use base height from VoidWorldGenerator if it is outside of the surface world, else it broke structures generation.
        // We only use UnderillaChunkGenerator base height to avoid a bug with the structure generation height.
        BukkitWorldInfo info = new BukkitWorldInfo(worldInfo);
        return this.engine.getBaseHeight(info, x, z, HEIGHTMAPS_MAP.get(heightMap));
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
            @NotNull ChunkData chunkData) {
        chunkProfiler.run(worldInfo.getUID(), chunkX, chunkZ,
                () -> generateSurfaceStep(worldInfo, random, chunkX, chunkZ, chunkData));
    }

    private void generateSurfaceStep(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
            @NotNull ChunkData chunkData) {
        if (outOfTheSurfaceWorldGenerator != null
                && isOutsideOfTheSurfaceWorld(chunkX * Underilla.CHUNK_SIZE, chunkZ * Underilla.CHUNK_SIZE)) {
            outOfTheSurfaceWorldGenerator.generateSurface(worldInfo, random, chunkX, chunkZ, chunkData);
            return;
        }

        String biomeKey = getBiomeKeyStringFromChunkCoordinates(worldInfo, chunkX, chunkZ);
        // if is a biome that should not be carved OR surface should not be preserved from carvers (== merge the world before).
        if (!Underilla.getUnderillaConfig().isBiomeInSet(SetBiomeStringKeys.APPLY_CARVERS_ONLY_ON_BIOMES, biomeKey) || !Underilla
                .getUnderillaConfig().isBiomeInSet(SetBiomeStringKeys.PRESERVE_SURFACE_WORLD_FROM_CAVERS_ONLY_ON_BIOMES, biomeKey)) {
            if (!tryPatchTerrain(chunkX, chunkZ, chunkData) && outOfTheSurfaceWorldGenerator != null) {
                outOfTheSurfaceWorldGenerator.generateSurface(worldInfo, random, chunkX, chunkZ, chunkData);
            }
        }
    }

    @Override
    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        chunkProfiler.run(worldInfo.getUID(), chunkX, chunkZ,
                () -> generateCavesStep(worldInfo, random, chunkX, chunkZ, chunkData));
    }

    private void generateCavesStep(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
            @NotNull ChunkData chunkData) {
        if (outOfTheSurfaceWorldGenerator != null
                && isOutsideOfTheSurfaceWorld(chunkX * Underilla.CHUNK_SIZE, chunkZ * Underilla.CHUNK_SIZE)) {
            outOfTheSurfaceWorldGenerator.generateCaves(worldInfo, random, chunkX, chunkZ, chunkData);
            return;
        }

        String biomeKey = getBiomeKeyStringFromChunkCoordinates(worldInfo, chunkX, chunkZ);
        // if is a biome that should be carved and surface should be preserved from carvers (== merge the world after).
        if (Underilla.getUnderillaConfig().isBiomeInSet(SetBiomeStringKeys.APPLY_CARVERS_ONLY_ON_BIOMES, biomeKey) && Underilla
                .getUnderillaConfig().isBiomeInSet(SetBiomeStringKeys.PRESERVE_SURFACE_WORLD_FROM_CAVERS_ONLY_ON_BIOMES, biomeKey)) {
            if (!tryPatchTerrain(chunkX, chunkZ, chunkData) && outOfTheSurfaceWorldGenerator != null) {
                outOfTheSurfaceWorldGenerator.generateCaves(worldInfo, random, chunkX, chunkZ, chunkData);
            }
        }
    }

    private boolean tryPatchTerrain(int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        BukkitChunkData data = new BukkitChunkData(chunkData, chunkX, chunkZ);
        return this.engine.tryPatchTerrain(data);
    }
    private static String getBiomeKeyStringFromChunkCoordinates(@NotNull WorldInfo worldInfo, int chunkX, int chunkZ) {
        return Bukkit.getWorld(worldInfo.getUID()).getBiome(chunkX * Underilla.CHUNK_SIZE, 0, chunkZ * Underilla.CHUNK_SIZE).getKey()
                .asString();
    }


    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        // Caves are vanilla generated, but they are carved underwater, this re-places the water blocks in case they were carved into.
        return List.of(new Populator(this.engine, this.chunkProfiler));
    }

    @Override
    public boolean shouldGenerateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (outOfTheSurfaceWorldGenerator != null
                && isOutsideOfTheSurfaceWorld(chunkX * Underilla.CHUNK_SIZE, chunkZ * Underilla.CHUNK_SIZE)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateNoise(worldInfo, random, chunkX, chunkZ);
        }

        return this.engine.getFlags().noise();
    }


    @Override
    public boolean shouldGenerateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (outOfTheSurfaceWorldGenerator != null
                && isOutsideOfTheSurfaceWorld(chunkX * Underilla.CHUNK_SIZE, chunkZ * Underilla.CHUNK_SIZE)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateSurface(worldInfo, random, chunkX, chunkZ);
        }

        // Must always return true, bedrock and deepslate layers are generated in this step
        return this.engine.getFlags().surface();
    }


    /**
     * Should generate caves with carvers.
     */
    @Override
    public boolean shouldGenerateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (outOfTheSurfaceWorldGenerator != null
                && isOutsideOfTheSurfaceWorld(chunkX * Underilla.CHUNK_SIZE, chunkZ * Underilla.CHUNK_SIZE)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateCaves(worldInfo, random, chunkX, chunkZ);
        }

        String biomeKey = getBiomeKeyStringFromChunkCoordinates(worldInfo, chunkX, chunkZ);
        return this.engine.getFlags().carvers()
                && Underilla.getUnderillaConfig().isBiomeInSet(SetBiomeStringKeys.APPLY_CARVERS_ONLY_ON_BIOMES, biomeKey);
    }

    @Override
    public boolean shouldGenerateDecorations(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (outOfTheSurfaceWorldGenerator != null
                && isOutsideOfTheSurfaceWorld(chunkX * Underilla.CHUNK_SIZE, chunkZ * Underilla.CHUNK_SIZE)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateDecorations(worldInfo, random, chunkX, chunkZ);
        }

        return this.engine.getFlags().features();
    }

    @Override
    public boolean shouldGenerateMobs(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (outOfTheSurfaceWorldGenerator != null
                && isOutsideOfTheSurfaceWorld(chunkX * Underilla.CHUNK_SIZE, chunkZ * Underilla.CHUNK_SIZE)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateMobs(worldInfo, random, chunkX, chunkZ);
        }

        return this.engine.getFlags().mobs();
    }

    @Override
    public boolean shouldGenerateStructures(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        if (outOfTheSurfaceWorldGenerator != null
                && isOutsideOfTheSurfaceWorld(chunkX * Underilla.CHUNK_SIZE, chunkZ * Underilla.CHUNK_SIZE)) {
            return outOfTheSurfaceWorldGenerator.shouldGenerateStructures(worldInfo, random, chunkX, chunkZ);
        }

        return this.engine.getFlags().structures();
    }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        int x = Math.min(Math.max(0, Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MIN_X)),
                Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MAX_X));
        int z = Math.min(Math.max(0, Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MIN_Z)),
                Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MAX_Z));
        return new Location(world, x, 100, z);
    }

    // Since 1.21.3 custom biomes are supported by paper.
    @Override
    public synchronized BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        if (biomeProvider == null) {
            BiomeProvider outsideSurfaceWorldBiomeProvider = outOfTheSurfaceWorldGenerator == null ? null
                    : outOfTheSurfaceWorldGenerator.getDefaultBiomeProvider(worldInfo);
            biomeProvider = new UnderillaBiomeProvider(engine, outsideSurfaceWorldBiomeProvider, chunkProfiler);
        }
        return biomeProvider;
    }

    public Map<String, Long> getBiomesPlaced() {
        UnderillaBiomeProvider currentBiomeProvider = biomeProvider;
        return currentBiomeProvider == null ? Map.of() : currentBiomeProvider.getBiomesPlaced();
    }


    private boolean isOutsideOfTheSurfaceWorld(int x, int z) {
        return x < Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MIN_X)
                || x >= Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MAX_X)
                || z < Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MIN_Z)
                || z >= Underilla.getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MAX_Z);
    }


    // CLASSES
    private static class Populator extends BlockPopulator {

        // FIELDS
        private final UnderillaEngine underillaEngine;
        private final ChunkGenerationProfiler chunkProfiler;


        // CONSTRUCTORS
        public Populator(UnderillaEngine underillaEngine, ChunkGenerationProfiler chunkProfiler) {
            this.underillaEngine = underillaEngine;
            this.chunkProfiler = chunkProfiler;
        }


        // OVERRITES
        @Override
        public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
            chunkProfiler.run(worldInfo.getUID(), chunkX, chunkZ,
                    () -> populateStep(worldInfo, chunkX, chunkZ, limitedRegion));
        }

        private void populateStep(WorldInfo worldInfo, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
            // If carvers are enabled in this biome & surface was not preserved from carvers & liquids are preserved from carvers.
            // => we need to re-insert the water blocks from surface world over the limits between the 2 world.

            if (Underilla.getUnderillaConfig().getBoolean(BooleanKeys.PRESERVE_LIQUID_FROM_CAVERS)) {
                String biomeKey = getBiomeKeyStringFromChunkCoordinates(worldInfo, chunkX, chunkZ);
                if (Underilla.getUnderillaConfig().isBiomeInSet(SetBiomeStringKeys.APPLY_CARVERS_ONLY_ON_BIOMES, biomeKey)
                        && !Underilla.getUnderillaConfig()
                                .isBiomeInSet(SetBiomeStringKeys.PRESERVE_SURFACE_WORLD_FROM_CAVERS_ONLY_ON_BIOMES, biomeKey)) {

                    BukkitRegionChunkData chunkData = new BukkitRegionChunkData(limitedRegion, chunkX, chunkZ, worldInfo.getMinHeight(),
                            worldInfo.getMaxHeight());
                    this.underillaEngine.tryPatchLiquids(chunkData);
                }
            }

            // The block populators are called after addVanillaDecorations(...) before light and mod spawn.
            // It's the right time to clean of blocks, but not yet for entities.
            // Calling it here should be thread safe and lag safe since Chunky will waut that the chunks is generated before stating new
            // chunks generation.
            if (Underilla.getUnderillaConfig().getBoolean(UnderillaConfig.BooleanKeys.CLEAN_BLOCKS_ENABLED)) {
                CleanBlocks.cleanBlocks(worldInfo, chunkX, chunkZ, limitedRegion);
            }
        }
    }

}
