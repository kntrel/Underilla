package com.kntrel.mc.underilla.paper.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.generation.NoodleCavesPolicy;
import com.kntrel.mc.underilla.core.generation.UnderillaFactory;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.BooleanKeys;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.SetBiomeStringKeys;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Maps Underilla's Paper configuration to the platform-neutral generation-plan API. */
public final class PaperGenerationPlanFactory {

    private PaperGenerationPlanFactory() {}

    public static WorldGenerationPlan create(
            String strategy,
            WorldReader surfaceWorld,
            @Nullable WorldReader undergroundWorld,
            UnderillaConfig config,
            BlockFactory blocks,
            Instrumenter instrumenter
    ) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(instrumenter, "instrumenter");

        UnderillaFactory.Builder builder = switch (strategy.trim().toUpperCase(Locale.ROOT)) {
            case "SURFACE" -> UnderillaFactory.surface(surfaceWorld);
            case "ABSOLUTE" -> UnderillaFactory.absolute(surfaceWorld);
            case "NONE" -> UnderillaFactory.none(surfaceWorld);
            default -> throw new IllegalArgumentException("Unknown patch strategy: " + strategy);
        };
        if (undergroundWorld != null) {
            builder.underground(undergroundWorld);
        }

        Predicate<com.kntrel.mc.underilla.core.api.Biome> exposedToCarvers = biome ->
                config.isBiomeInSet(SetBiomeStringKeys.APPLY_CARVERS_ONLY_ON_BIOMES, biome.getName())
                        && !config.isBiomeInSet(
                                SetBiomeStringKeys.PRESERVE_SURFACE_WORLD_FROM_CAVERS_ONLY_ON_BIOMES,
                                biome.getName());

        return builder
                .instrumenter(instrumenter)
                .verticalRange(config.generationAreaMinY(), config.generationAreaMaxY())
                .maximumCaveY(config.maxHeightOfCaves())
                .surfaceDepth(
                        config.mergeDepth(),
                        config.adaptiveMaxMergeDepth(),
                        config.adaptiveMinHiddenBlocksMergeDepth())
                .chunkCacheSize(config.cacheSize())
                .blocks(blocks)
                .generationArea(
                        config.generationAreaMinX(),
                        config.generationAreaMinZ(),
                        config.generationAreaMaxX(),
                        config.generationAreaMaxZ())
                .surfaceOnlyBiomes(config::isSurfaceWorldOnlyBiome)
                .preservedGeneratedBiomes(config::shouldPreserveBiome)
                .preserveGeneratedBiomesOnlyUnderSurface(config.preserveBiomesOnlyUnderSurface())
                .ignoredSurfaceBlocks(config::isIgnoredForSurfaceCalculation)
                .keptSurfaceBlocks(block -> config.shouldKeepSurfaceBlockInCaves(block.getName()))
                .surfaceBlockTransformer(block -> transformSurfaceBlock(block, config, blocks))
                .surfaceBiomeUseTopYOnly(config.surfaceBiomeUseTopYOnly())
                .carvers(config.carversEnabled())
                .features(config.vanillaPopulationEnabled())
                .mobs(config.vanillaPopulationEnabled())
                .structures(config.structuresEnabled())
                .noodleCaves(NoodleCavesPolicy.surface(
                        exposedToCarvers,
                        config.getBoolean(BooleanKeys.PRESERVE_LIQUID_FROM_CAVERS)))
                .build();
    }

    private static Block transformSurfaceBlock(Block block, UnderillaConfig config, BlockFactory blocks) {
        String replacement = config.surfaceBlockReplacement(block.getName());
        return replacement == null ? block : blocks.create(replacement);
    }
}
