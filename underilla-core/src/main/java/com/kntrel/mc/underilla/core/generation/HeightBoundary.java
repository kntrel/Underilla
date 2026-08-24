package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Calculates a per-column boundary from the reference world's terrain height. */
public final class HeightBoundary implements Boundary {

    private final WorldReader surfaceWorld;
    private final Block air;
    private final int minimumY;
    private final int maximumY;
    private final int hardSurfaceBoundary;
    private final int mergeDepth;
    private final int adaptiveMaximumDepth;
    private final int adaptiveMinimumHiddenDepth;
    private final Predicate<String> surfaceWorldOnlyBiome;
    private final Predicate<String> ignoredSurfaceBlock;

    public HeightBoundary(
            WorldReader surfaceWorld,
            Block air,
            int minimumY,
            int maximumY,
            int hardSurfaceBoundary,
            int mergeDepth,
            int adaptiveMaximumDepth,
            int adaptiveMinimumHiddenDepth,
            Predicate<String> surfaceWorldOnlyBiome,
            Predicate<String> ignoredSurfaceBlock
    ) {
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.air = Objects.requireNonNull(air, "air");
        this.minimumY = minimumY;
        this.maximumY = maximumY;
        this.hardSurfaceBoundary = hardSurfaceBoundary;
        this.mergeDepth = mergeDepth;
        this.adaptiveMaximumDepth = adaptiveMaximumDepth;
        this.adaptiveMinimumHiddenDepth = adaptiveMinimumHiddenDepth;
        this.surfaceWorldOnlyBiome = Objects.requireNonNull(surfaceWorldOnlyBiome, "surfaceWorldOnlyBiome");
        this.ignoredSurfaceBlock = Objects.requireNonNull(ignoredSurfaceBlock, "ignoredSurfaceBlock");
    }

    @Override
    public int at(int globalX, int globalZ) {
        int clampedHardSurfaceBoundary = Math.max(minimumY, Math.min(hardSurfaceBoundary, maximumY));
        if (clampedHardSurfaceBoundary <= minimumY) {
            return minimumY;
        }

        String biomeName = surfaceWorld.getBiomeName(globalX, maximumY, globalZ);
        if (surfaceWorldOnlyBiome.test(biomeName)) {
            return minimumY;
        }

        int lowerBlock = clampedHardSurfaceBoundary + mergeDepth;
        while (!isSurfaceBlock(surfaceWorld.blockAt(globalX, lowerBlock, globalZ).orElse(air))
                && lowerBlock > minimumY) {
            lowerBlock--;
        }

        int remainingAdaptiveDepth = adaptiveMaximumDepth - mergeDepth;
        int finalDepth = mergeDepth;
        if (remainingAdaptiveDepth > 0) {
            int exposedBlocks = 0;
            while (remainingAdaptiveDepth > 0
                    && haveNonSolidNeighbour(surfaceWorld, globalX, lowerBlock - exposedBlocks, globalZ)
                    && lowerBlock > minimumY) {
                exposedBlocks++;
                remainingAdaptiveDepth--;
            }
            finalDepth = Math.max(mergeDepth, exposedBlocks + adaptiveMinimumHiddenDepth);
        }

        return lowerBlock - finalDepth;
    }

    private boolean isSurfaceBlock(Block block) {
        return block.isSolid() && !ignoredSurfaceBlock.test(block.getName());
    }

    private static boolean haveNonSolidNeighbour(WorldReader world, int x, int y, int z) {
        return Stream.of(world.blockAt(x + 1, y, z), world.blockAt(x - 1, y, z), world.blockAt(x, y, z + 1),
                world.blockAt(x, y, z - 1)).filter(Optional::isPresent).map(Optional::get).anyMatch(block -> !block.isSolid());
    }

}
