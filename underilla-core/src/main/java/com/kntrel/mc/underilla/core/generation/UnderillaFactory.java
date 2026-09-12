package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.Entity;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.cleanup.BlockCleanupPatcher;
import com.kntrel.mc.underilla.core.cleanup.EntityCleanupPatcher;
import com.kntrel.mc.underilla.core.patch.DeferredPatcher;
import com.kntrel.mc.underilla.core.patch.ChunkBlock;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.patch.PerBlockChunkPatcher;
import com.kntrel.mc.underilla.core.patch.TransformationBlockPatcher;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.reader.DiskWorldReader;
import com.kntrel.mc.underilla.core.reference.*;
import com.kntrel.mc.underilla.core.reference.mask.AbsoluteWorldMask;
import com.kntrel.mc.underilla.core.reference.mask.ReferenceHeightWorldMask;
import com.kntrel.mc.underilla.core.reference.mask.UnionWorldMask;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import com.kntrel.mc.underilla.core.vector.Vector;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
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
        return new Builder(Strategy.ABSOLUTE, referenceWorld, true);
    }

    public static Builder surface(WorldReader referenceWorld) {
        return new Builder(Strategy.SURFACE, referenceWorld, true);
    }

    public static Builder none(WorldReader referenceWorld) {
        return new Builder(Strategy.NONE, referenceWorld, true);
    }

    private enum Strategy {
        ABSOLUTE,
        SURFACE,
        NONE
    }

    /** Mutable input collector for one strategy-specific plan. */
    public static final class Builder {

        private final Strategy strategy;
        private WorldReader referenceWorld;
        private WorldReader undergroundWorld;
        private Instrumenter instrumenter;
        private Integer minimumY;
        private Integer maximumY;
        private Integer maximumCaveY;
        private int mergeDepth;
        private int adaptiveMaximumDepth;
        private int adaptiveMinimumHiddenDepth;
        private int chunkCacheSize = 128;
        private BlockFactory blocks;
        private GenerationArea generationArea = GenerationArea.everywhere();
        private Predicate<ID> surfaceOnlyBiome = _ -> false;
        private Predicate<ID> preservedGeneratedBiome = _ -> false;
        private Predicate<ID> ignoredSurfaceBlock = _ -> false;
        private Predicate<Block> keptSurfaceBlock;
        private UnaryOperator<Block> surfaceBlockTransformer;
        private Function<ID, Optional<ID>> cleanupSupportReplacement;
        private Function<ID, Optional<ID>> cleanupBlockReplacement;
        private Predicate<Entity> cleanupEntityRemoval;
        private Consumer<Entity> cleanupEntityTransformer;
        private boolean surfaceBiomeUseTopYOnly;
        private boolean preserveGeneratedBiomesOnlyUnderSurface;
        private boolean surfaceFill;
        private boolean carversEnabled = true;
        private boolean featuresEnabled = true;
        private boolean mobsEnabled = true;
        private boolean structuresEnabled = true;
        private NoodleCavesPolicy noodleCavesPolicy = NoodleCavesPolicy.underground();

        private Builder(Strategy strategy, WorldReader referenceWorld, boolean surfaceFill) {
            this.strategy = Objects.requireNonNull(strategy, "strategy");
            this.referenceWorld = Objects.requireNonNull(referenceWorld, "referenceWorld");
            this.surfaceFill = surfaceFill;
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

        public Builder surfaceOnlyBiomes(Predicate<ID> surfaceOnlyBiome) {
            this.surfaceOnlyBiome = Objects.requireNonNull(surfaceOnlyBiome, "surfaceOnlyBiome");
            return this;
        }

        public Builder preservedGeneratedBiomes(Predicate<ID> preservedGeneratedBiome) {
            this.preservedGeneratedBiome = Objects.requireNonNull(
                    preservedGeneratedBiome, "preservedGeneratedBiome");
            return this;
        }

        public Builder preserveGeneratedBiomesOnlyUnderSurface(boolean enabled) {
            preserveGeneratedBiomesOnlyUnderSurface = enabled;
            return this;
        }

        public Builder ignoredSurfaceBlocks(Predicate<ID> ignoredSurfaceBlock) {
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

        /** Copies reference blocks above the generated surface even when they are outside the world mask. */
        public Builder surfaceFill(boolean enabled) {
            surfaceFill = enabled;
            return this;
        }

        /** Configures block support and replacement cleanup after vanilla features are generated. */
        public Builder blockCleanup(
                Function<ID, Optional<ID>> supportReplacement,
                Function<ID, Optional<ID>> blockReplacement
        ) {
            this.cleanupSupportReplacement = Objects.requireNonNull(supportReplacement, "supportReplacement");
            this.cleanupBlockReplacement = Objects.requireNonNull(blockReplacement, "blockReplacement");
            return this;
        }

        /** Configures entity removal and final transformation after the generated chunk becomes live. */
        public Builder entityCleanup(
                Predicate<Entity> shouldRemove,
                Consumer<Entity> survivingEntityTransformer
        ) {
            this.cleanupEntityRemoval = Objects.requireNonNull(shouldRemove, "shouldRemove");
            this.cleanupEntityTransformer = Objects.requireNonNull(
                    survivingEntityTransformer, "survivingEntityTransformer");
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
            ChunkCache chunkCache = new ChunkCache(chunkCacheSize);
            referenceWorld = bindReader(referenceWorld, chunkCache);
            undergroundWorld = bindReader(undergroundWorld, chunkCache);

            int configuredMinimumY = requiredValue(minimumY, "verticalRange");
            int configuredMaximumY = requiredValue(maximumY, "verticalRange");
            int configuredMaximumCaveY = maximumCaveY == null ? configuredMaximumY : maximumCaveY;
            BlockFactory configuredBlocks = Objects.requireNonNull(blocks, "blocks");
            Supplier<Block> configuredAir = configuredBlocks::air;
            WorldMask worldMask = worldMask(configuredMinimumY, configuredMaximumY, configuredMaximumCaveY,
                    configuredAir.get(), chunkCache);
            WorldGenerationPlanBuilder plan = WorldGenerationPlan.build();
            if (instrumenter != null) {
                plan.instrumenter(instrumenter);
            }

            List<Patcher<ChunkData>> afterFeatures = new ArrayList<>();
            if (cleanupSupportReplacement != null) {
                afterFeatures.add(new BlockCleanupPatcher(
                        configuredBlocks,
                        cleanupSupportReplacement,
                        cleanupBlockReplacement));
            }
            afterFeatures.add(new ReferenceWorldEntityPatcher(referenceWorld));

            plan    .coverage(this::coversChunk)
                    .biomePatch(new SurfaceBiomePatcher(
                            referenceWorld,
                            worldMask,
                            generationArea,
                            configuredMaximumY,
                            surfaceBiomeUseTopYOnly,
                            surfaceOnlyBiome,
                            preservedGeneratedBiome,
                            preserveGeneratedBiomesOnlyUnderSurface
                    ))
                    .afterFeatures(afterFeatures);
            if (cleanupEntityRemoval != null) {
                plan.afterLoad(new EntityCleanupPatcher(cleanupEntityRemoval, cleanupEntityTransformer));
            }
            plan.altimeter(new SurfaceAltimeter(referenceWorld, configuredAir));

            if (noodleCavesPolicy instanceof NoodleCavesPolicy.Underground) {
                plan.afterCarvers(terrainPatcher(
                        worldMask,
                        configuredMinimumY,
                        configuredAir
                ));
            } else if (noodleCavesPolicy instanceof NoodleCavesPolicy.Surface surfacePolicy) {
                Patcher<ChunkData> referenceWorldPatcher = Patchers.referenceWorldPatcher(
                        referenceWorld,
                        worldMask,
                        surfaceFill,
                        configuredMinimumY,
                        configuredAir,
                        keptSurfaceBlock,
                        surfaceBlockTransformations()
                );
                DeferredPatcher deferredSurface = new DeferredPatcher(referenceWorldPatcher,
                        deferredWritePredicate(surfacePolicy, surfaceBiomeUseTopYOnly, configuredMaximumY),
                        chunkCache);
                List<Patcher<ChunkData>> patchers = new ArrayList<>();
                if (undergroundWorld != null) {
                    patchers.add(Patchers.referenceWorldPatcher(
                            undergroundWorld,
                            worldMask.inverted(),
                            false,
                            minimumY,
                            blocks::air,
                            null,
                            List.of()
                    ));
                }
                patchers.add(deferredSurface);
                plan.afterSurface(patchers);
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

        private static WorldReader bindReader(WorldReader reader, ChunkCache cache) {
            return reader instanceof DiskWorldReader disk ? disk.withChunkCache(cache) : reader;
        }

        private WorldMask worldMask(int minimumY, int maximumY, int maximumCaveY, Block air, ChunkCache cache) {
            return switch (strategy) {
                case ABSOLUTE -> new AbsoluteWorldMask(maximumCaveY, minimumY, maximumY);
                case SURFACE -> new ReferenceHeightWorldMask(
                        referenceWorld,
                        air,
                        minimumY,
                        maximumY,
                        maximumCaveY,
                        mergeDepth,
                        adaptiveMaximumDepth,
                        adaptiveMinimumHiddenDepth,
                        surfaceOnlyBiome,
                        ignoredSurfaceBlock,
                        cache
                );
                case NONE -> new AbsoluteWorldMask(minimumY);
            };
        }

        private Patcher<ChunkData> terrainPatcher(
                WorldMask worldMask,
                int minimumY,
                Supplier<Block> air
        ) {
            if (!surfaceFill) {
                return combinedTerrainPatcher(worldMask, worldMask, minimumY, air);
            }
            return new WorldHeightMaskPatcher(minimumY, heightMask -> combinedTerrainPatcher(
                    worldMask,
                    new UnionWorldMask(heightMask, worldMask),
                    minimumY,
                    air
            ));
        }

        private Patcher<ChunkData> combinedTerrainPatcher(
                WorldMask undergroundMask,
                WorldMask surfaceMask,
                int minimumY,
                Supplier<Block> air
        ) {
            Patcher<ChunkBlock> surface = Patchers.referenceWorldBlockPatcher(
                    referenceWorld,
                    surfaceMask,
                    air,
                    keptSurfaceBlock,
                    surfaceBlockTransformations()
            );
            if (undergroundWorld == null) {
                return available(referenceWorld, perBlock(minimumY, surface));
            }

            Patcher<ChunkBlock> underground = Patchers.referenceWorldBlockPatcher(
                    undergroundWorld,
                    undergroundMask.inverted(),
                    air,
                    null,
                    List.of()
            );
            Patcher<ChunkData> surfaceOnly = perBlock(minimumY, surface);
            Patcher<ChunkData> undergroundOnly = perBlock(minimumY, underground);
            Patcher<ChunkData> both = perBlock(minimumY, underground, surface);

            return Patcher.<ChunkData>iff(chunk -> chunkExists(referenceWorld, chunk))
                    .then(Patcher.<ChunkData>iff(chunk -> chunkExists(undergroundWorld, chunk))
                            .then(both)
                            .otherwise(surfaceOnly)
                            .end())
                    .otherwise(Patcher.<ChunkData>iff(chunk -> chunkExists(undergroundWorld, chunk))
                            .then(undergroundOnly)
                            .end())
                    .end();
        }

        private static Patcher<ChunkData> available(
                WorldReader world,
                Patcher<ChunkData> patcher
        ) {
            return Patcher.<ChunkData>iff(chunk -> chunkExists(world, chunk))
                    .then(patcher)
                    .end();
        }

        @SafeVarargs
        private static Patcher<ChunkData> perBlock(
                int minimumY,
                Patcher<ChunkBlock>... patchers
        ) {
            Patcher<ChunkBlock> aboveMinimumY = Patcher.<ChunkBlock>iff(
                            block -> block.y() >= minimumY)
                    .then(Patcher.sequence(patchers))
                    .end();
            return new PerBlockChunkPatcher(aboveMinimumY);
        }

        private static boolean chunkExists(WorldReader world, ChunkData chunk) {
            return world.readChunk(chunk.getChunkX(), chunk.getChunkZ()).isPresent();
        }

        private List<Patcher<ChunkBlock>> surfaceBlockTransformations() {
            return surfaceBlockTransformer == null
                    ? List.of()
                    : List.of(TransformationBlockPatcher.fromBlocks(surfaceBlockTransformer));
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
