package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Merges the reference world down to a configurable depth beneath each surface column. */
public final class SurfaceMerger extends AbstractMerger {

    private final WorldReader surfaceWorld;
    private final Map<ColumnCoordinate, Integer> boundaryCache = new ConcurrentHashMap<>();

    public SurfaceMerger(WorldReader surfaceWorld, GenerationContext context) {
        super(context);
        this.surfaceWorld = surfaceWorld;
    }

    @Override
    protected int mergeBoundaryY(int globalX, int globalZ) {
        ColumnCoordinate column = new ColumnCoordinate(globalX, globalZ);
        return boundaryCache.computeIfAbsent(column,
                ignored -> getLowerBlockOfSurfaceWorldYLevel(surfaceWorld, context(), globalX, globalZ));
    }

    private static int getLowerBlockOfSurfaceWorldYLevel(WorldReader world, GenerationContext context, int globalX, int globalZ) {
        GenerationConfig config = context.config();
        int minimalPossibleY = config.generationAreaMinY();
        int maxHeightOfCaves = Math.max(minimalPossibleY,
                Math.min(config.maxHeightOfCaves(), config.generationAreaMaxY()));
        if (maxHeightOfCaves <= minimalPossibleY) {
            return minimalPossibleY;
        }

        String biomeName = world.getBiomeName(globalX, config.generationAreaMaxY(), globalZ);
        if (config.isSurfaceWorldOnlyBiome(biomeName)) {
            return minimalPossibleY;
        }

        int mergeDepth = config.mergeDepth();
        int lowerBlock = maxHeightOfCaves + mergeDepth;
        while (!isSurfaceBlock(world.blockAt(globalX, lowerBlock, globalZ).orElse(context.blocks().air()), config)
                && lowerBlock > minimalPossibleY) {
            lowerBlock--;
        }

        int remainingAdaptiveDepth = config.adaptiveMaxMergeDepth() - mergeDepth;
        int finalDepth = mergeDepth;
        if (remainingAdaptiveDepth > 0) {
            int exposedBlocks = 0;
            while (remainingAdaptiveDepth > 0 && haveNonSolidNeighbour(world, globalX, lowerBlock - exposedBlocks, globalZ)
                    && lowerBlock > minimalPossibleY) {
                exposedBlocks++;
                remainingAdaptiveDepth--;
            }
            finalDepth = Math.max(mergeDepth, exposedBlocks + config.adaptiveMinHiddenBlocksMergeDepth());
        }

        return lowerBlock - finalDepth;
    }

    private static boolean isSurfaceBlock(Block block, GenerationConfig config) {
        return block.isSolid() && !config.isIgnoredForSurfaceCalculation(block.getName());
    }

    private static boolean haveNonSolidNeighbour(WorldReader world, int x, int y, int z) {
        return Stream.of(world.blockAt(x + 1, y, z), world.blockAt(x - 1, y, z), world.blockAt(x, y, z + 1),
                world.blockAt(x, y, z - 1)).filter(Optional::isPresent).map(Optional::get).anyMatch(block -> !block.isSolid());
    }

    private record ColumnCoordinate(int x, int z) {}
}
