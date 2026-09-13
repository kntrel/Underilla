package com.kntrel.mc.underilla.paper.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.generation.NoodleCavesPolicy;
import com.kntrel.mc.underilla.core.generation.UnderillaFactory;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.paper.Underilla;
import com.kntrel.mc.underilla.paper.impl.BukkitEntity;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.BooleanKeys;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.SetBiomeStringKeys;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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

        NoodleCavesPolicy noodleCavesPolicy = noodleCavesPolicy(
                config.carversEnabled(),
                config.getSetBiomeString(SetBiomeStringKeys.APPLY_CARVERS_ONLY_ON_BIOMES),
                config.getSetBiomeString(SetBiomeStringKeys.APPLY_CARVERS_EXCEPT_ON_BIOMES),
                config.getBoolean(BooleanKeys.PRESERVE_SURFACE_WORLD_FROM_CAVERS),
                config.getSetBiomeString(SetBiomeStringKeys.PRESERVE_SURFACE_WORLD_FROM_CAVERS_ONLY_ON_BIOMES),
                config.getSetBiomeString(SetBiomeStringKeys.PRESERVE_SURFACE_WORLD_FROM_CAVERS_EXCEPT_ON_BIOMES),
                config.getSetBiomeString(SetBiomeStringKeys.SURFACE_WORLD_ONLY_ON_THIS_BIOMES),
                config.getBoolean(BooleanKeys.PRESERVE_LIQUID_FROM_CAVERS));

        builder
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
                .keptSurfaceBlocks(block -> config.shouldKeepSurfaceBlockInCaves(block.id()))
                .surfaceBlockTransformer(block -> transformSurfaceBlock(block, config, blocks))
                .surfaceBiomeUseTopYOnly(config.surfaceBiomeUseTopYOnly())
                .carvers(config.carversEnabled())
                .features(config.vanillaPopulationEnabled())
                .mobs(config.vanillaPopulationEnabled())
                .structures(config.structuresEnabled())
                .noodleCaves(noodleCavesPolicy);
        if (config.getBoolean(BooleanKeys.CLEAN_BLOCKS_ENABLED)) {
            builder.blockCleanup(config::cleanupSupportReplacement, config::cleanupBlockReplacement);
        }
        if (config.getBoolean(BooleanKeys.CLEAN_ENTITIES_ENABLED)) {
            builder.entityCleanup(
                    entity -> config.shouldRemoveEntity(entity.id()),
                    entity -> {
                        if (entity instanceof BukkitEntity bukkitEntity
                                && Underilla.getInstance().hasEndEntityTransformer()) {
                            Underilla.getInstance().getEndEntityTransformer().accept(bukkitEntity.getEntity());
                        }
                    });
        }
        return builder.build();
    }

    private static Block transformSurfaceBlock(Block block, UnderillaConfig config, BlockFactory blocks) {
        return config.surfaceBlockReplacement(block.id()).map(blocks::create).orElse(block);
    }

    static NoodleCavesPolicy noodleCavesPolicy(
            boolean carversEnabled,
            Set<ID> carvedOnlyOn,
            Set<ID> carvedExceptOn,
            boolean surfaceProtectionEnabled,
            Set<ID> protectedOnlyOn,
            Set<ID> protectedExceptOn,
            Set<ID> surfaceOnlyBiomes,
            boolean restoreLiquids
    ) {
        BiomeSelection carved = BiomeSelection.of(carversEnabled, carvedOnlyOn, carvedExceptOn)
                .excluding(surfaceOnlyBiomes);
        BiomeSelection protectedSurface = BiomeSelection.of(
                carversEnabled && surfaceProtectionEnabled,
                protectedOnlyOn,
                protectedExceptOn);
        if (carved.isSubsetOf(protectedSurface)) {
            return NoodleCavesPolicy.underground();
        }
        return NoodleCavesPolicy.surface(
                biome -> carved.contains(biome.id()) && !protectedSurface.contains(biome.id()),
                restoreLiquids);
    }

    /** Compact biome selection: included IDs, or every ID except the stored exclusions. */
    private record BiomeSelection(boolean complemented, Set<ID> ids) {

        private BiomeSelection {
            ids = Set.copyOf(Objects.requireNonNull(ids, "ids"));
        }

        static BiomeSelection of(boolean enabled, Set<ID> onlyOn, Set<ID> exceptOn) {
            Objects.requireNonNull(onlyOn, "onlyOn");
            Objects.requireNonNull(exceptOn, "exceptOn");
            if (!enabled) {
                return new BiomeSelection(false, Set.of());
            }
            return onlyOn.isEmpty()
                    ? new BiomeSelection(true, exceptOn)
                    : new BiomeSelection(false, onlyOn);
        }

        BiomeSelection excluding(Set<ID> excluded) {
            Objects.requireNonNull(excluded, "excluded");
            java.util.HashSet<ID> updated = new java.util.HashSet<>(ids);
            if (complemented) {
                updated.addAll(excluded);
            } else {
                updated.removeAll(excluded);
            }
            return new BiomeSelection(complemented, updated);
        }

        boolean contains(ID biome) {
            return complemented != ids.contains(biome);
        }

        boolean isSubsetOf(BiomeSelection other) {
            if (!complemented && !other.complemented) {
                return other.ids.containsAll(ids);
            }
            if (!complemented) {
                return ids.stream().noneMatch(other.ids::contains);
            }
            if (other.complemented) {
                return ids.containsAll(other.ids);
            }
            return false;
        }
    }
}
