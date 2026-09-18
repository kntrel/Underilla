package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.Entity;
import com.kntrel.mc.underilla.core.reader.EntityView;
import java.util.Objects;

/** Runs a chunk patcher through a view exposing only one vertical range. */
public final class VerticalBoundChunkPatcher implements Patcher<ChunkData> {

    private final Patcher<ChunkData> delegate;
    private final int minimumY;
    private final int maximumY;

    public VerticalBoundChunkPatcher(Patcher<ChunkData> delegate, int minimumY, int maximumY) {
        if (maximumY < minimumY) {
            throw new IllegalArgumentException("maximumY must be greater than or equal to minimumY");
        }
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.minimumY = minimumY;
        this.maximumY = maximumY;
    }

    @Override
    public void patch(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        int boundedMinimumY = clamp(minimumY, targetChunk.getMinHeight(), targetChunk.getMaxHeight());
        int boundedMaximumY = clamp(maximumY, targetChunk.getMinHeight(), targetChunk.getMaxHeight());
        if (boundedMinimumY == targetChunk.getMinHeight() && boundedMaximumY == targetChunk.getMaxHeight()) {
            delegate.patch(targetChunk);
            return;
        }
        delegate.patch(new VerticalBoundChunkData(targetChunk, boundedMinimumY, boundedMaximumY));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private record VerticalBoundChunkData(
            ChunkData delegate,
            int minimumY,
            int maximumY
    ) implements ChunkData {

        private VerticalBoundChunkData {
            Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public int getMaxHeight() {
            return maximumY;
        }

        @Override
        public int getMinHeight() {
            return minimumY;
        }

        @Override
        public int getChunkX() {
            return delegate.getChunkX();
        }

        @Override
        public int getChunkZ() {
            return delegate.getChunkZ();
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            requireY(y);
            return delegate.getBlock(x, y, z);
        }

        @Override
        public Biome getBiome(int x, int y, int z) {
            requireY(y);
            return delegate.getBiome(x, y, z);
        }

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Block block) {
            requireYRange(yMin, yMax);
            delegate.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, block);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            requireY(y);
            delegate.setBlock(x, y, z, block);
        }

        @Override
        public void setBiome(int x, int y, int z, Biome biome) {
            requireY(y);
            delegate.setBiome(x, y, z, biome);
        }

        @Override
        public void addEntity(EntityView entity) {
            delegate.addEntity(entity);
        }

        @Override
        public Iterable<Entity> entities() {
            return delegate.entities();
        }

        private void requireY(int y) {
            if (y < minimumY || y >= maximumY) {
                throw new IndexOutOfBoundsException(
                        "Y outside vertical bounds: " + y + " not in [" + minimumY + ", " + maximumY + ")");
            }
        }

        private void requireYRange(int yMinimum, int yMaximum) {
            if (yMinimum < minimumY || yMaximum > maximumY || yMaximum < yMinimum) {
                throw new IndexOutOfBoundsException(
                        "Y range outside vertical bounds: [" + yMinimum + ", " + yMaximum + ") not in [" + minimumY + ", " + maximumY + ")");
            }
        }
    }
}
