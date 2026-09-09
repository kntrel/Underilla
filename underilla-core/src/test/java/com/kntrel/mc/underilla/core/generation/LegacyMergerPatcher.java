package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.cache.ChunkCache;

import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.patch.ChunkPatcherPipeline;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.reference.CavePatcher;
import com.kntrel.mc.underilla.core.reference.mask.ReferenceHeightWorldMask;
import com.kntrel.mc.underilla.core.reference.ReferenceWorldPatcher;
import com.kntrel.mc.underilla.core.reference.mask.AbsoluteWorldMask;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;

import java.util.Objects;

/** Builds the production patcher pipeline used by the merger characterization fixture. */
final class LegacyMergerPatcher {

    private final ChunkPatcher patcher;

    private LegacyMergerPatcher(ChunkPatcher patcher) {
        this.patcher = patcher;
    }

    static LegacyMergerPatcher absolute(
            WorldReader surfaceWorld,
            WorldReader cavesWorld,
            TestGenerationConfig config,
            BlockFactory blocks
    ) {
        Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(blocks, "blocks");
        WorldMask worldMask = new AbsoluteWorldMask(config.maxHeightOfCaves(),
                config.generationAreaMinY(), config.generationAreaMaxY());
        return new LegacyMergerPatcher(pipeline(cavesWorld, worldMask, config, blocks,
                referenceWorldPatcher(surfaceWorld, worldMask, config, blocks)));
    }

    static LegacyMergerPatcher surface(
            WorldReader surfaceWorld,
            WorldReader cavesWorld,
            TestGenerationConfig config,
            BlockFactory blocks
    ) {
        Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(blocks, "blocks");
        WorldMask worldMask = surfaceWorldMask(surfaceWorld, config, blocks);
        return new LegacyMergerPatcher(pipeline(cavesWorld, worldMask, config, blocks,
                referenceWorldPatcher(surfaceWorld, worldMask, config, blocks)));
    }

    void patch(ChunkData chunk) {
        patcher.patch(chunk);
    }

    private static ChunkPatcher pipeline(
            WorldReader cavesWorld,
            WorldMask worldMask,
            TestGenerationConfig config,
            BlockFactory blocks,
            ChunkPatcher strategyPatcher
    ) {
        if (cavesWorld == null) {
            return strategyPatcher;
        }
        return new ChunkPatcherPipeline(
                new CavePatcher(cavesWorld, worldMask, config.generationAreaMinY(), blocks::air),
                strategyPatcher);
    }

    private static ChunkPatcher referenceWorldPatcher(
            WorldReader surfaceWorld,
            WorldMask worldMask,
            TestGenerationConfig config,
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

    private static WorldMask surfaceWorldMask(
            WorldReader surfaceWorld,
            TestGenerationConfig config,
            BlockFactory blocks
    ) {
        return new ReferenceHeightWorldMask(surfaceWorld, blocks.air(),
                        config.generationAreaMinY(), config.generationAreaMaxY(), config.maxHeightOfCaves(),
                        config.mergeDepth(), config.adaptiveMaxMergeDepth(),
                        config.adaptiveMinHiddenBlocksMergeDepth(), config::isSurfaceWorldOnlyBiome,
                        config::isIgnoredForSurfaceCalculation, new ChunkCache(config.cacheSize())
        );
    }
}
