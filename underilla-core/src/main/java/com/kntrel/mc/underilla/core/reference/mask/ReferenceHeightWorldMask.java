package com.kntrel.mc.underilla.core.reference.mask;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.World;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.context.ChunkView;
import com.kntrel.mc.underilla.core.api.context.BiomeContext;
import com.kntrel.mc.underilla.core.api.context.BlockContext;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.cache.TopicChunkCache;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;
import java.util.Arrays;
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
    private final Predicate<BiomeContext> surfaceWorldOnlyBiome;
    private final Predicate<BlockContext> ignoredSurfaceBlock;

    public ReferenceHeightWorldMask(
            WorldReader surfaceWorld,
            Block air,
            int minimumY,
            int maximumY,
            int hardSurfaceBoundary,
            int mergeDepth,
            int adaptiveMaximumDepth,
            int adaptiveMinimumHiddenDepth,
            Predicate<BiomeContext> surfaceWorldOnlyBiome,
            Predicate<BlockContext> ignoredSurfaceBlock,
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
        return contains(null, globalX, y, globalZ);
    }

    @Override
    public boolean contains(World targetWorld, int globalX, int y, int globalZ) {
        int size = GenerationConstants.CHUNK_SIZE;
        ColumnBoundaries chunk = boundaries.getOrCompute(
                Math.floorDiv(globalX, size),
                Math.floorDiv(globalZ, size),
                ColumnBoundaries::new
        );
        int column = Math.floorMod(globalZ, size) * size + Math.floorMod(globalX, size);
        WorldKey target = targetWorld == null ? null : new WorldKey(targetWorld.name(), targetWorld.dimension(),
                targetWorld.minHeight(), targetWorld.maxHeight());
        synchronized (chunk) {
            if (!Objects.equals(chunk.target, target)) {
                Arrays.fill(chunk.known, false);
                chunk.target = target;
            }
            if (!chunk.known[column]) {
                chunk.heights[column] = boundaryAt(targetWorld, globalX, globalZ);
                chunk.known[column] = true;
            }
            return y > chunk.heights[column];
        }
    }

    private static final class ColumnBoundaries {
        private WorldKey target;
        private final int[] heights = new int[GenerationConstants.CHUNK_SIZE * GenerationConstants.CHUNK_SIZE];
        private final boolean[] known = new boolean[heights.length];
    }

    private record WorldKey(String name, ID dimension, int minimumY, int maximumY) {}

    private int boundaryAt(World targetWorld, int globalX, int globalZ) {
        int clampedHardSurfaceBoundary = Math.max(minimumY, Math.min(hardSurfaceBoundary, maximumY));
        if (clampedHardSurfaceBoundary <= minimumY) {
            return minimumY;
        }

        Optional<Biome> biome = surfaceWorld.biomeAt(globalX, maximumY, globalZ);
        ChunkView chunk = ChunkView.of(Math.floorDiv(globalX, GenerationConstants.CHUNK_SIZE),
                Math.floorDiv(globalZ, GenerationConstants.CHUNK_SIZE));
        if (biome.isPresent() && surfaceWorldOnlyBiome.test(BiomeContext.of(
                chunk, surfaceWorld, biome.get(), targetWorld, null))) {
            return minimumY;
        }

        int lowerBlock = clampedHardSurfaceBoundary + mergeDepth;
        while (!isSurfaceBlock(targetWorld, globalX, lowerBlock, globalZ)
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

    private boolean isSurfaceBlock(World targetWorld, int x, int y, int z) {
        Block block = surfaceWorld.blockAt(x, y, z).orElse(air);
        ChunkView chunk = ChunkView.of(Math.floorDiv(x, GenerationConstants.CHUNK_SIZE),
                Math.floorDiv(z, GenerationConstants.CHUNK_SIZE));
        return block.isSolid() && !ignoredSurfaceBlock.test(
                BlockContext.of(
                        chunk,
                        surfaceWorld,
                        surfaceWorld.biomeAt(x, y, z).orElse(null), block,
                        targetWorld, null, null));
    }

    private static boolean haveNonSolidNeighbour(WorldReader world, int x, int y, int z) {
        return Stream.of(world.blockAt(x + 1, y, z), world.blockAt(x - 1, y, z), world.blockAt(x, y, z + 1),
                world.blockAt(x, y, z - 1)).filter(Optional::isPresent).map(Optional::get).anyMatch(block -> !block.isSolid());
    }

}
