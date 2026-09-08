package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.cache.ChunkCache;

import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.patch.ChunkPatcherPipeline;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;

/**
 * Keeps the characterization fixture's original construction API while running
 * the production patcher pipeline.
 */
final class LegacyMergerPatcher {

    private final ChunkPatcher patcher;

    LegacyMergerPatcher(AbsoluteMerger merger, WorldReader surfaceWorld, WorldReader cavesWorld) {
        Objects.requireNonNull(merger, "merger");
        Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        GenerationConfig config = merger.context().config();
        var blocks = merger.context().blocks();
        WorldMask worldMask = new AbsoluteWorldMask(config.maxHeightOfCaves(),
                config.generationAreaMinY(), config.generationAreaMaxY());
        this.patcher = pipeline(cavesWorld, worldMask, merger.context(),
                referenceWorldPatcher(surfaceWorld, worldMask, config, blocks));
    }

    LegacyMergerPatcher(SurfaceMerger merger, WorldReader surfaceWorld, WorldReader cavesWorld) {
        Objects.requireNonNull(merger, "merger");
        Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        GenerationConfig config = merger.context().config();
        var blocks = merger.context().blocks();
        WorldMask worldMask = surfaceWorldMask(surfaceWorld, merger.context());
        this.patcher = pipeline(cavesWorld, worldMask, merger.context(),
                referenceWorldPatcher(surfaceWorld, worldMask, config, blocks));
    }

    void patch(ChunkData chunk) {
        patcher.patch(chunk);
    }

    private static ChunkPatcher pipeline(WorldReader cavesWorld, WorldMask worldMask, GenerationContext context,
            ChunkPatcher strategyPatcher) {
        if (cavesWorld == null) {
            return strategyPatcher;
        }
        return new ChunkPatcherPipeline(
                new CavePatcher(cavesWorld, worldMask, context.config().generationAreaMinY(), context.blocks()::air),
                strategyPatcher);
    }

    private static ChunkPatcher referenceWorldPatcher(
            WorldReader surfaceWorld,
            WorldMask worldMask,
            GenerationConfig config,
            BlockFactory blocks
    ) {
        return new ReferenceWorldPatcher(
                surfaceWorld,
                worldMask,
                config.generationAreaMinY(),
                blocks::air,
                block -> config.shouldKeepSurfaceBlockInCaves(block.id()),
                block -> {
                    return config.surfaceBlockReplacement(block.id()).map(blocks::create).orElse(block);
                });
    }

    private static WorldMask surfaceWorldMask(WorldReader surfaceWorld, GenerationContext context) {
        GenerationConfig config = context.config();
        return new ReferenceHeightWorldMask(surfaceWorld, context.blocks().air(),
                        config.generationAreaMinY(), config.generationAreaMaxY(), config.maxHeightOfCaves(),
                        config.mergeDepth(), config.adaptiveMaxMergeDepth(),
                        config.adaptiveMinHiddenBlocksMergeDepth(), config::isSurfaceWorldOnlyBiome,
                        config::isIgnoredForSurfaceCalculation, new ChunkCache(config.cacheSize())
        );
    }
}
