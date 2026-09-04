package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.patch.DeferredPatcher;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Entry point for composing an Underilla world-generation plan.
 *
 * <p>Its strategy-named entry points make the chosen reference-terrain strategy explicit while the
 * returned builder collects the inputs used to assemble a generation plan.</p>
 */
public final class UnderillaFactory {

    private UnderillaFactory() {}

    public static Builder absolute(WorldReader referenceWorld) {
        return new Builder(Strategy.ABSOLUTE, referenceWorld);
    }

    public static Builder surface(WorldReader referenceWorld) {
        return new Builder(Strategy.SURFACE, referenceWorld);
    }

    public static Builder none(WorldReader referenceWorld) {
        return new Builder(Strategy.NONE, referenceWorld);
    }

    private enum Strategy {
        ABSOLUTE,
        SURFACE,
        NONE
    }

    /** Mutable input collector for one strategy-specific plan. */
    public static final class Builder {

        private final Strategy strategy;
        private final WorldReader referenceWorld;
        private WorldReader undergroundWorld;
        private Instrumenter instrumenter;
        private GenerationConfig config;
        private BlockFactory blocks;
        private NoodleCavesPolicy noodleCavesPolicy = NoodleCavesPolicy.underground();

        private Builder(Strategy strategy, WorldReader referenceWorld) {
            this.strategy = Objects.requireNonNull(strategy, "strategy");
            this.referenceWorld = Objects.requireNonNull(referenceWorld, "referenceWorld");
        }

        public Builder underground(WorldReader undergroundWorld) {
            this.undergroundWorld = Objects.requireNonNull(undergroundWorld, "undergroundWorld");
            return this;
        }

        public Builder instrumenter(Instrumenter instrumenter) {
            this.instrumenter = Objects.requireNonNull(instrumenter, "instrumenter");
            return this;
        }

        public Builder config(GenerationConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        public Builder blocks(BlockFactory blocks) {
            this.blocks = Objects.requireNonNull(blocks, "blocks");
            return this;
        }

        public Builder noodleCaves(NoodleCavesPolicy noodleCavesPolicy) {
            this.noodleCavesPolicy = Objects.requireNonNull(noodleCavesPolicy, "noodleCavesPolicy");
            return this;
        }

        /**
         * Builds the complete phase plan for this strategy and noodle-cave policy.
         */
        public WorldGenerationPlan build() {
            GenerationConfig generationConfig = Objects.requireNonNull(config, "config");
            BlockFactory blockFactory = Objects.requireNonNull(blocks, "blocks");
            Boundary boundary = boundary(generationConfig, blockFactory);
            ChunkPatcher surfacePatcher = new SurfacePatcher(referenceWorld, boundary, generationConfig, blockFactory);
            WorldGenerationPlanBuilder plan = WorldGenerationPlan.build();
            if (instrumenter != null) {
                plan.instrumenter(instrumenter);
            }

            if (noodleCavesPolicy instanceof NoodleCavesPolicy.Underground) {
                List<ChunkPatcher> patchers = terrainPatchersBeforeSurface(boundary, generationConfig, blockFactory);
                patchers.add(surfacePatcher);
                plan.afterCarvers(patchers.toArray(ChunkPatcher[]::new));
            } else if (noodleCavesPolicy instanceof NoodleCavesPolicy.Surface surfacePolicy) {
                DeferredPatcher deferredSurface = new DeferredPatcher(surfacePatcher, deferredWritePredicate(surfacePolicy));
                List<ChunkPatcher> patchers = terrainPatchersBeforeSurface(boundary, generationConfig, blockFactory);
                patchers.add(deferredSurface);
                plan.afterSurface(patchers.toArray(ChunkPatcher[]::new));
                plan.afterCarvers(deferredSurface.applier());
            }

            plan.noise(strategy != Strategy.NONE)
                    // Noodle-cave policies decide how carvers affect copied terrain, never whether
                    // vanilla carvers run at all.
                    .carvers(true)
                    .features(generationConfig.vanillaPopulationEnabled())
                    .mobs(generationConfig.vanillaPopulationEnabled())
                    .structures(generationConfig.structuresEnabled());
            return plan.done();
        }

        private Boundary boundary(GenerationConfig generationConfig, BlockFactory blockFactory) {
            return switch (strategy) {
                case ABSOLUTE -> new AbsoluteBoundary(generationConfig.maxHeightOfCaves(),
                        generationConfig.generationAreaMinY(), generationConfig.generationAreaMaxY());
                case SURFACE -> new CachedBoundary(
                        new HeightBoundary(
                                referenceWorld,
                                blockFactory.air(),
                                generationConfig.generationAreaMinY(),
                                generationConfig.generationAreaMaxY(),
                                generationConfig.maxHeightOfCaves(),
                                generationConfig.mergeDepth(),
                                generationConfig.adaptiveMaxMergeDepth(),
                                generationConfig.adaptiveMinHiddenBlocksMergeDepth(),
                                generationConfig::isSurfaceWorldOnlyBiome,
                                generationConfig::isIgnoredForSurfaceCalculation
                        ),
                        generationConfig.cacheSize()
                    );
                    case NONE -> new AbsoluteBoundary(generationConfig.generationAreaMinY());
            };
        }

        private List<ChunkPatcher> terrainPatchersBeforeSurface(Boundary boundary, GenerationConfig generationConfig, BlockFactory blockFactory) {
            List<ChunkPatcher> patchers = new ArrayList<>();
            if (undergroundWorld != null) {
                patchers.add(new CavePatcher(undergroundWorld, boundary, generationConfig, blockFactory));
            }
            return patchers;
        }

        private BiPredicate<Vector<Integer>, ChunkData> deferredWritePredicate(NoodleCavesPolicy.Surface policy) {
            BiPredicate<Vector<Integer>, ChunkData> mayWriteBeforeCarvers = matchingReferenceBiome(policy.predicate());
            if (policy.restoreLiquids()) {
                mayWriteBeforeCarvers = mayWriteBeforeCarvers.and(referenceBlockIsNotLiquid());
            }
            return mayWriteBeforeCarvers.negate();
        }

        private BiPredicate<Vector<Integer>, ChunkData> matchingReferenceBiome(Predicate<Biome> predicate) {
            if (config.surfaceBiomeUseTopYOnly()) {
                int topY = config.generationAreaMaxY();
                return (position, targetChunk) -> referenceWorld
                        .biomeAt(globalX(position, targetChunk), topY, globalZ(position, targetChunk))
                        .filter(predicate)
                        .isPresent();
            }
            return (position, targetChunk) -> referenceWorld
                    .biomeAt(globalX(position, targetChunk), position.y(), globalZ(position, targetChunk))
                    .filter(predicate)
                    .isPresent();
        }

        private BiPredicate<Vector<Integer>, ChunkData> referenceBlockIsNotLiquid() {
            return (position, targetChunk) -> referenceWorld
                    .blockAt(globalX(position, targetChunk), position.y(), globalZ(position, targetChunk))
                    .map(Block::isLiquid)
                    .map(isLiquid -> !isLiquid)
                    .orElse(true);
        }

        private static int globalX(Vector<Integer> position, ChunkData targetChunk) {
            return targetChunk.getChunkX() * GenerationConstants.CHUNK_SIZE + position.x();
        }

        private static int globalZ(Vector<Integer> position, ChunkData targetChunk) {
            return targetChunk.getChunkZ() * GenerationConstants.CHUNK_SIZE + position.z();
        }
    }
}
