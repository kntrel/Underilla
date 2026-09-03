package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.List;

/** Composes patchers and generation policies for each supported mode. */
public final class PatcherFactory {

    private PatcherFactory() {}

    public static PatchingPlan surface(WorldReader surfaceWorld,
            WorldReader cavesWorld, GenerationContext context) {
        return plan(surfaceWorld, cavesWorld, context, heightBoundary(surfaceWorld, context), true);
    }

    public static PatchingPlan absolute(WorldReader surfaceWorld,
            WorldReader cavesWorld, GenerationContext context) {
        GenerationConfig config = context.config();
        Boundary boundary = new AbsoluteBoundary(config.maxHeightOfCaves(),
                config.generationAreaMinY(), config.generationAreaMaxY());
        return plan(surfaceWorld, cavesWorld, context, boundary, true);
    }

    public static PatchingPlan none(WorldReader surfaceWorld,
            WorldReader cavesWorld, GenerationContext context) {
        Boundary boundary = new AbsoluteBoundary(context.config().generationAreaMinY());
        return plan(surfaceWorld, cavesWorld, context, boundary, false);
    }

    private static PatchingPlan plan(WorldReader surfaceWorld, WorldReader cavesWorld,
            GenerationContext context, Boundary boundary, boolean generateNoise) {
        ChunkPatcher surfacePatcher = new SurfacePatcher(surfaceWorld, boundary, context);
        List<ChunkPatcher> terrainPatchers = cavesWorld == null
                ? List.of(surfacePatcher)
                : List.of(new CavePatcher(cavesWorld, boundary, context), surfacePatcher);
        ChunkPatcher liquidPatcher = new LiquidPatcher(surfaceWorld, boundary, context);
        return new PatchingPlan(terrainPatchers, liquidPatcher, boundary, generateNoise);
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
