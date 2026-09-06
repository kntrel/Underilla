package com.kntrel.mc.underilla.paper.generation;

import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.paper.Underilla;
import com.kntrel.mc.underilla.paper.impl.BukkitBiome;
import com.kntrel.mc.underilla.paper.impl.BukkitBiomeData;
import com.kntrel.mc.underilla.paper.profiling.ChunkGenerationProfiler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides Underilla's reference-world biomes while preserving configured vanilla cave biomes. */
public final class UnderillaBiomeProvider extends BiomeProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnderillaBiomeProvider.class);
    private final WorldGenerationPlan generationPlan;
    private final ChunkGenerationProfiler chunkProfiler;
    private volatile BiomeProvider vanillaProvider;
    private final @Nullable BiomeProvider outOfBoundsProvider;
    private final Map<String, Long> biomesPlaced = new ConcurrentHashMap<>();
    private long lastWarningPrinted;

    public UnderillaBiomeProvider(
            WorldGenerationPlan generationPlan,
            @Nullable BiomeProvider outOfBoundsBiomeProvider,
            ChunkGenerationProfiler chunkProfiler
    ) {
        this.generationPlan = generationPlan;
        this.outOfBoundsProvider = outOfBoundsBiomeProvider;
        this.chunkProfiler = chunkProfiler;
    }

    public Map<String, Long> getBiomesPlaced() { return biomesPlaced; }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        return chunkProfiler.call(
                worldInfo.getUID(),
                Math.floorDiv(x, Underilla.CHUNK_SIZE),
                Math.floorDiv(z, Underilla.CHUNK_SIZE),
                () -> getBiomeForPosition(worldInfo, x, y, z));
    }

    private @NotNull Biome getBiomeForPosition(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        Biome vanillaBiome = getVanillaBiomeProvider(worldInfo).getBiome(worldInfo, x, y, z);
        BukkitBiomeData biomeData = new BukkitBiomeData(vanillaBiome, x, y, z);
        if (!generationPlan.biomePatch().patch(biomeData) && outOfBoundsProvider != null) {
            Biome fallbackBiome = outOfBoundsProvider.getBiome(worldInfo, x, y, z);
            countBiome(fallbackBiome.getKey().asString());
            return fallbackBiome;
        }

        Biome selectedBiome = biomeData.getBukkitBiome();
        if (selectedBiome != null) {
            countBiome(selectedBiome.getKey().asString());
            return selectedBiome;
        }

        warning("Use vanilla biome because selected biome '" + biomeData.get().getName()
                + "' is unavailable at " + x + " " + y + " " + z);
        countBiome(vanillaBiome.getKey().asString());
        return vanillaBiome;
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) { return BukkitBiome.getAllBiomesList(); }

    private BiomeProvider getVanillaBiomeProvider(WorldInfo worldInfo) {
        BiomeProvider provider = vanillaProvider;
        if (provider != null) {
            return provider;
        }
        synchronized (this) {
            if (vanillaProvider == null) {
                vanillaProvider = worldInfo.vanillaBiomeProvider();
                LOGGER.info("Vanilla biome provider initialized: {}", vanillaProvider);
            }
            return vanillaProvider;
        }
    }

    private void countBiome(String key) { biomesPlaced.merge(key, 1L, Long::sum); }

    private synchronized void warning(String message) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastWarningPrinted > Underilla.MS_PER_SECOND) {
            LOGGER.warn(message);
            lastWarningPrinted = currentTime;
        }
    }
}
