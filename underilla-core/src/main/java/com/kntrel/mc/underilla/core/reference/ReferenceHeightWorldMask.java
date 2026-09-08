package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.cache.TopicChunkCache;
import com.kntrel.mc.underilla.core.generation.WorldMask;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Selects positions above the reference world's final merge boundary.
 * Boundaries are calculated lazily per column and retained in the shared chunk cache.
 * Use one mask configuration per cache; its reference world and settings must remain fixed.
 */
public final class ReferenceHeightWorldMask implements WorldMask {

    private final TopicChunkCache<ColumnBoundaries> boundaries;

    private final WorldReader surfaceWorld;
    private final Block air;
    private final int minimumY;
    private final int maximumY;
    private final int hardSurfaceBoundary;
    private final int mergeDepth;
    private final int adaptiveMaximumDepth;
    private final int adaptiveMinimumHiddenDepth;
    private final Predicate<ID> surfaceWorldOnlyBiome;
    private final Predicate<ID> ignoredSurfaceBlock;

    public ReferenceHeightWorldMask(
            WorldReader surfaceWorld,
            Block air,
            int minimumY,
            int maximumY,
            int hardSurfaceBoundary,
            int mergeDepth,
            int adaptiveMaximumDepth,
            int adaptiveMinimumHiddenDepth,
            Predicate<ID> surfaceWorldOnlyBiome,
            Predicate<ID> ignoredSurfaceBlock,
            ChunkCache cache
    ) {
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.boundaries = new TopicChunkCache<>(cache, ColumnBoundaries.class);
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
    public boolean contains(int globalX, int y, int globalZ) {
        int size = GenerationConstants.CHUNK_SIZE;
        ColumnBoundaries chunk = boundaries.getOrCompute(
                Math.floorDiv(globalX, size),
                Math.floorDiv(globalZ, size),
                ColumnBoundaries::new
        );
        int column = Math.floorMod(globalZ, size) * size + Math.floorMod(globalX, size);
        synchronized (chunk) {
            if (!chunk.known[column]) {
                chunk.heights[column] = boundaryAt(globalX, globalZ);
                chunk.known[column] = true;
            }
            return y > chunk.heights[column];
        }
    }

    private static final class ColumnBoundaries {
        private final int[] heights = new int[GenerationConstants.CHUNK_SIZE * GenerationConstants.CHUNK_SIZE];
        private final boolean[] known = new boolean[heights.length];
    }

    private int boundaryAt(int globalX, int globalZ) {
        int clampedHardSurfaceBoundary = Math.max(minimumY, Math.min(hardSurfaceBoundary, maximumY));
        if (clampedHardSurfaceBoundary <= minimumY) {
            return minimumY;
        }

        ID biome = surfaceWorld.getBiomeID(globalX, maximumY, globalZ);
        if (surfaceWorldOnlyBiome.test(biome)) {
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
        return block.isSolid() && !ignoredSurfaceBlock.test(block.id());
    }

    private static boolean haveNonSolidNeighbour(WorldReader world, int x, int y, int z) {
        return Stream.of(world.blockAt(x + 1, y, z), world.blockAt(x - 1, y, z), world.blockAt(x, y, z + 1),
                world.blockAt(x, y, z - 1)).filter(Optional::isPresent).map(Optional::get).anyMatch(block -> !block.isSolid());
    }

}
