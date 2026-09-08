package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.patch.ChunkPatcherPipeline;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.reader.DiskWorldReader;
import java.util.List;
import java.util.function.UnaryOperator;

/** Composes patchers and generation policies for each supported mode. */
public final class PatcherFactory {

    private PatcherFactory() {}

    public static PatchingPlan surface(WorldReader surfaceWorld,
            WorldReader cavesWorld, GenerationContext context) {
        ChunkCache cache = new ChunkCache(context.config().cacheSize());
        surfaceWorld = bindReader(surfaceWorld, cache);
        cavesWorld = bindReader(cavesWorld, cache);
        return plan(surfaceWorld, cavesWorld, context, surfaceWorldMask(surfaceWorld, context, cache), true);
    }

    public static PatchingPlan absolute(WorldReader surfaceWorld,
            WorldReader cavesWorld, GenerationContext context) {
        ChunkCache cache = new ChunkCache(context.config().cacheSize());
        surfaceWorld = bindReader(surfaceWorld, cache);
        cavesWorld = bindReader(cavesWorld, cache);
        GenerationConfig config = context.config();
        WorldMask worldMask = new AbsoluteWorldMask(config.maxHeightOfCaves(),
                config.generationAreaMinY(), config.generationAreaMaxY());
        return plan(surfaceWorld, cavesWorld, context, worldMask, true);
    }

    public static PatchingPlan none(WorldReader surfaceWorld,
            WorldReader cavesWorld, GenerationContext context) {
        ChunkCache cache = new ChunkCache(context.config().cacheSize());
        surfaceWorld = bindReader(surfaceWorld, cache);
        cavesWorld = bindReader(cavesWorld, cache);
        WorldMask worldMask = new AbsoluteWorldMask(context.config().generationAreaMinY());
        return plan(surfaceWorld, cavesWorld, context, worldMask, false);
    }

    private static PatchingPlan plan(WorldReader surfaceWorld, WorldReader cavesWorld,
            GenerationContext context, WorldMask worldMask, boolean generateNoise) {
        GenerationConfig config = context.config();
        var blocks = context.blocks();
        ChunkPatcher terrainPatcher = new WorldHeightPatcher(config.generationAreaMinY(), heightMask -> {
            ChunkPatcher referenceWorldPatcher = new ReferenceWorldPatcher(
                    surfaceWorld,
                    new UnionWorldMask(heightMask, worldMask),
                    config.generationAreaMinY(),
                    blocks::air,
                    block -> config.shouldKeepSurfaceBlockInCaves(block.id()),
                    surfaceBlockTransformer(config, blocks)
            );
            return cavesWorld == null
                    ? referenceWorldPatcher
                    : new ChunkPatcherPipeline(
                            new CavePatcher(cavesWorld, worldMask, config.generationAreaMinY(), blocks::air),
                            referenceWorldPatcher
                    );
        });
        List<ChunkPatcher> terrainPatchers = List.of(terrainPatcher);
        ChunkPatcher liquidPatcher = new LiquidPatcher(surfaceWorld, worldMask);
        return new PatchingPlan(terrainPatchers, liquidPatcher, worldMask, generateNoise);
    }

    private static WorldReader bindReader(WorldReader reader, ChunkCache cache) {
        return reader instanceof DiskWorldReader disk ? disk.withChunkCache(cache) : reader;
    }

    private static WorldMask surfaceWorldMask(WorldReader surfaceWorld, GenerationContext context, ChunkCache cache) {
        GenerationConfig config = context.config();
        return new ReferenceHeightWorldMask(surfaceWorld, context.blocks().air(),
                        config.generationAreaMinY(), config.generationAreaMaxY(), config.maxHeightOfCaves(),
                        config.mergeDepth(), config.adaptiveMaxMergeDepth(),
                        config.adaptiveMinHiddenBlocksMergeDepth(), config::isSurfaceWorldOnlyBiome,
                        config::isIgnoredForSurfaceCalculation, cache
        );
    }

    private static UnaryOperator<Block> surfaceBlockTransformer(
            GenerationConfig config,
            BlockFactory blocks
    ) {
        return block -> {
            return config.surfaceBlockReplacement(block.id()).map(blocks::create).orElse(block);
        };
    }
}
