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
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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
        private Integer minimumY;
        private Integer maximumY;
        private Integer maximumCaveY;
        private int mergeDepth;
        private int adaptiveMaximumDepth;
        private int adaptiveMinimumHiddenDepth;
        private int chunkCacheSize = 1;
        private BlockFactory blocks;
        private GenerationArea generationArea = GenerationArea.everywhere();
        private Predicate<String> surfaceOnlyBiome = _ -> false;
        private Predicate<String> preservedGeneratedBiome = _ -> false;
        private Predicate<String> ignoredSurfaceBlock = _ -> false;
        private Predicate<Block> keptSurfaceBlock = _ -> false;
        private UnaryOperator<Block> surfaceBlockTransformer = UnaryOperator.identity();
        private boolean surfaceBiomeUseTopYOnly;
        private boolean preserveGeneratedBiomesOnlyUnderSurface;
        private boolean carversEnabled = true;
        private boolean featuresEnabled = true;
        private boolean mobsEnabled = true;
        private boolean structuresEnabled = true;
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

        public Builder verticalRange(int minimumY, int maximumY) {
            if (maximumY < minimumY) {
                throw new IllegalArgumentException("maximumY must be greater than or equal to minimumY");
            }
            this.minimumY = minimumY;
            this.maximumY = maximumY;
            return this;
        }

        public Builder maximumCaveY(int maximumCaveY) {
            this.maximumCaveY = maximumCaveY;
            return this;
        }

        public Builder surfaceDepth(int mergeDepth, int adaptiveMaximumDepth, int adaptiveMinimumHiddenDepth) {
            this.mergeDepth = mergeDepth;
            this.adaptiveMaximumDepth = adaptiveMaximumDepth;
            this.adaptiveMinimumHiddenDepth = adaptiveMinimumHiddenDepth;
            return this;
        }

        public Builder chunkCacheSize(int cacheSize) {
            if (cacheSize < 1) {
                throw new IllegalArgumentException("cacheSize must be at least 1");
            }
            this.chunkCacheSize = cacheSize;
            return this;
        }

        public Builder blocks(BlockFactory blocks) {
            this.blocks = Objects.requireNonNull(blocks, "blocks");
            return this;
        }

        public Builder generationArea(int minimumX, int minimumZ, int maximumX, int maximumZ) {
            this.generationArea = new GenerationArea(minimumX, minimumZ, maximumX, maximumZ);
            return this;
        }

        public Builder surfaceOnlyBiomes(Predicate<String> surfaceOnlyBiome) {
            this.surfaceOnlyBiome = Objects.requireNonNull(surfaceOnlyBiome, "surfaceOnlyBiome");
            return this;
        }

        public Builder preservedGeneratedBiomes(Predicate<String> preservedGeneratedBiome) {
            this.preservedGeneratedBiome = Objects.requireNonNull(
                    preservedGeneratedBiome, "preservedGeneratedBiome");
            return this;
        }

        public Builder preserveGeneratedBiomesOnlyUnderSurface(boolean enabled) {
            preserveGeneratedBiomesOnlyUnderSurface = enabled;
            return this;
        }

        public Builder ignoredSurfaceBlocks(Predicate<String> ignoredSurfaceBlock) {
            this.ignoredSurfaceBlock = Objects.requireNonNull(ignoredSurfaceBlock, "ignoredSurfaceBlock");
            return this;
        }

        public Builder keptSurfaceBlocks(Predicate<Block> keptSurfaceBlock) {
            this.keptSurfaceBlock = Objects.requireNonNull(keptSurfaceBlock, "keptSurfaceBlock");
            return this;
        }

        public Builder surfaceBlockTransformer(UnaryOperator<Block> surfaceBlockTransformer) {
            this.surfaceBlockTransformer = Objects.requireNonNull(surfaceBlockTransformer, "surfaceBlockTransformer");
            return this;
        }

        public Builder surfaceBiomeUseTopYOnly(boolean surfaceBiomeUseTopYOnly) {
            this.surfaceBiomeUseTopYOnly = surfaceBiomeUseTopYOnly;
            return this;
        }

        public Builder carvers(boolean enabled) {
            carversEnabled = enabled;
            return this;
        }

        public Builder features(boolean enabled) {
            featuresEnabled = enabled;
            return this;
        }

        public Builder mobs(boolean enabled) {
            mobsEnabled = enabled;
            return this;
        }

        public Builder structures(boolean enabled) {
            structuresEnabled = enabled;
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
            int configuredMinimumY = requiredValue(minimumY, "verticalRange");
            int configuredMaximumY = requiredValue(maximumY, "verticalRange");
            int configuredMaximumCaveY = maximumCaveY == null ? configuredMaximumY : maximumCaveY;
            BlockFactory configuredBlocks = Objects.requireNonNull(blocks, "blocks");
            Supplier<Block> configuredAir = configuredBlocks::air;
            Boundary boundary = boundary(configuredMinimumY, configuredMaximumY, configuredMaximumCaveY,
                    configuredAir.get());
            ChunkPatcher surfacePatcher = new SurfacePatcher(referenceWorld, boundary, configuredMinimumY,
                    configuredAir, keptSurfaceBlock, surfaceBlockTransformer);
            WorldGenerationPlanBuilder plan = WorldGenerationPlan.build();
            if (instrumenter != null) {
                plan.instrumenter(instrumenter);
            }

            plan.coverage(this::coversChunk)
                    .biomePatch(new SurfaceBiomePatcher(
                            referenceWorld,
                            boundary,
                            generationArea,
                            configuredMaximumY,
                            surfaceBiomeUseTopYOnly,
                            surfaceOnlyBiome,
                            preservedGeneratedBiome,
                            preserveGeneratedBiomesOnlyUnderSurface
                    ))
                    .afterFeatures(new ReferenceWorldEntityPatcher(referenceWorld));
            plan.altimeter(new SurfaceAltimeter(referenceWorld, configuredAir));

            if (noodleCavesPolicy instanceof NoodleCavesPolicy.Underground) {
                List<ChunkPatcher> patchers = terrainPatchersBeforeSurface(
                        boundary, configuredMinimumY, configuredAir);
                patchers.add(surfacePatcher);
                plan.afterCarvers(patchers.toArray(ChunkPatcher[]::new));
            } else if (noodleCavesPolicy instanceof NoodleCavesPolicy.Surface surfacePolicy) {
                DeferredPatcher deferredSurface = new DeferredPatcher(surfacePatcher,
                        deferredWritePredicate(surfacePolicy, surfaceBiomeUseTopYOnly, configuredMaximumY),
                        chunkCacheSize);
                List<ChunkPatcher> patchers = terrainPatchersBeforeSurface(
                        boundary, configuredMinimumY, configuredAir);
                patchers.add(deferredSurface);
                plan.afterSurface(patchers.toArray(ChunkPatcher[]::new));
                plan.afterCarvers(deferredSurface.applier());
            }

            plan.noise(strategy != Strategy.NONE)
                    // Noodle-cave policies decide how carvers affect copied terrain, never whether
                    // vanilla carvers run at all.
                    .carvers(carversEnabled)
                    .features(featuresEnabled)
                    .mobs(mobsEnabled)
                    .structures(structuresEnabled);
            return plan.done();
        }

        private Boundary boundary(int minimumY, int maximumY, int maximumCaveY, Block air) {
            return switch (strategy) {
                case ABSOLUTE -> new AbsoluteBoundary(maximumCaveY, minimumY, maximumY);
                case SURFACE -> new CachedBoundary(
                        new HeightBoundary(
                                referenceWorld,
                                air,
                                minimumY,
                                maximumY,
                                maximumCaveY,
                                mergeDepth,
                                adaptiveMaximumDepth,
                                adaptiveMinimumHiddenDepth,
                                surfaceOnlyBiome,
                                ignoredSurfaceBlock
                        ),
                        chunkCacheSize
                    );
                case NONE -> new AbsoluteBoundary(minimumY);
            };
        }

        private List<ChunkPatcher> terrainPatchersBeforeSurface(
                Boundary boundary,
                int minimumY,
                Supplier<Block> air
        ) {
            List<ChunkPatcher> patchers = new ArrayList<>();
            if (undergroundWorld != null) {
                patchers.add(new CavePatcher(undergroundWorld, boundary, minimumY, air));
            }
            return patchers;
        }

        private BiPredicate<Vector<Integer>, ChunkData> deferredWritePredicate(
                NoodleCavesPolicy.Surface policy,
                boolean useTopYOnly,
                int topY
        ) {
            BiPredicate<Vector<Integer>, ChunkData> mayWriteBeforeCarvers = matchingReferenceBiome(
                    policy.predicate(), useTopYOnly, topY);
            if (policy.restoreLiquids()) {
                mayWriteBeforeCarvers = mayWriteBeforeCarvers.and(referenceBlockIsNotLiquid());
            }
            return mayWriteBeforeCarvers.negate();
        }

        private BiPredicate<Vector<Integer>, ChunkData> matchingReferenceBiome(
                Predicate<Biome> predicate,
                boolean useTopYOnly,
                int topY
        ) {
            if (useTopYOnly) {
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

        private static int requiredValue(Integer value, String source) {
            if (value == null) {
                throw new IllegalStateException(source + " must be configured");
            }
            return value;
        }

        private boolean coversChunk(int chunkX, int chunkZ) {
            int blockX = chunkX * GenerationConstants.CHUNK_SIZE;
            int blockZ = chunkZ * GenerationConstants.CHUNK_SIZE;
            return generationArea.contains(blockX, blockZ)
                    && referenceWorld.readChunk(chunkX, chunkZ).isPresent();
        }

        private static int globalX(Vector<Integer> position, ChunkData targetChunk) {
            return targetChunk.getChunkX() * GenerationConstants.CHUNK_SIZE + position.x();
        }

        private static int globalZ(Vector<Integer> position, ChunkData targetChunk) {
            return targetChunk.getChunkZ() * GenerationConstants.CHUNK_SIZE + position.z();
        }
    }
}
