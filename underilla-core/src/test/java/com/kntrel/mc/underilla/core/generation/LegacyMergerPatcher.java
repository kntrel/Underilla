package com.kntrel.mc.underilla.core.generation;

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
        Boundary boundary = new AbsoluteBoundary(config.maxHeightOfCaves(),
                config.generationAreaMinY(), config.generationAreaMaxY());
        this.patcher = pipeline(cavesWorld, boundary, merger.context(),
                new SurfacePatcher(surfaceWorld, boundary, merger.context()));
    }

    LegacyMergerPatcher(SurfaceMerger merger, WorldReader surfaceWorld, WorldReader cavesWorld) {
        Objects.requireNonNull(merger, "merger");
        Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        Boundary boundary = heightBoundary(surfaceWorld, merger.context());
        this.patcher = pipeline(cavesWorld, boundary, merger.context(),
                new SurfacePatcher(surfaceWorld, boundary, merger.context()));
    }

    void patch(ChunkData chunk) {
        patcher.patch(chunk);
    }

    private static ChunkPatcher pipeline(WorldReader cavesWorld, Boundary boundary, GenerationContext context,
            ChunkPatcher strategyPatcher) {
        if (cavesWorld == null) {
            return strategyPatcher;
        }
        return new ChunkPatcherPipeline(new CavePatcher(cavesWorld, boundary, context), strategyPatcher);
    }

    private static Boundary heightBoundary(WorldReader surfaceWorld, GenerationContext context) {
        GenerationConfig config = context.config();
        Boundary heightBoundary = new HeightBoundary(surfaceWorld, context.blocks().air(),
                config.generationAreaMinY(), config.generationAreaMaxY(), config.maxHeightOfCaves(),
                config.mergeDepth(), config.adaptiveMaxMergeDepth(), config.adaptiveMinHiddenBlocksMergeDepth(),
                config::isSurfaceWorldOnlyBiome, config::isIgnoredForSurfaceCalculation);
        return new CachedBoundary(heightBoundary, config.cacheSize());
    }
}
