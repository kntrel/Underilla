package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.reader.EntityView;
import com.kntrel.mc.underilla.core.vector.IntVector;
import com.kntrel.mc.underilla.core.vector.Vector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Runs a patcher against a chunk-data view that caches selected writes for a later phase.
 *
 * <p>The cache is FIFO and chunk-bounded. A missing batch is recomputed by the applier through a
 * view that applies only writes selected by {@code shouldDefer}.</p>
 */
public final class DeferredPatcher implements ChunkPatcher {

    private static final int DEFAULT_CACHE_CAPACITY = 256;

    //FIELDS
    private final ChunkPatcher delegate;
    private final DeferredTasks deferredWrites;
    private final BiPredicate<Vector<Integer>, ChunkData> shouldDefer;
    private final Applier applier;


    //CONSTRUCTORS
    public DeferredPatcher(
            ChunkPatcher delegate,
            BiPredicate<Vector<Integer>, ChunkData> shouldDefer
    ) {
        this(delegate, shouldDefer, DEFAULT_CACHE_CAPACITY);
    }

    public DeferredPatcher(
            ChunkPatcher delegate,
            BiPredicate<Vector<Integer>, ChunkData> shouldDefer,
            int cacheCapacity
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.deferredWrites = new DeferredTasks(cacheCapacity);
        this.shouldDefer = Objects.requireNonNull(shouldDefer, "shouldDefer");
        this.applier = new Applier(this.delegate, this.deferredWrites, this.shouldDefer);
    }


    //API
    public ChunkPatcher applier() {
        return this.applier;
    }


    //IMPLEMENTATION
    @Override
    public void patch(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        DeferredBlockWrites writes = new DeferredBlockWrites();
        delegate.patch(new ChunkDataProxy(targetChunk, writes, shouldDefer));
        deferredWrites.put(coordinateOf(targetChunk), writes);
    }


    //HELPERS
    private static ChunkCoordinate coordinateOf(ChunkData chunk) {
        return new ChunkCoordinate(chunk.getChunkX(), chunk.getChunkZ());
    }



    //SUBTYPES -----------------------------------------------------------------------------------------

    private record ChunkCoordinate(int x, int z) {}

    private record ChunkDataProxy(
            ChunkData delegate,
            DeferredBlockWrites deferredWrites,
            BiPredicate<Vector<Integer>, ChunkData> shouldDefer
    ) implements ChunkData {

        private ChunkDataProxy {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(shouldDefer, "shouldDefer");
        }

        @Override
        public int getMaxHeight() {
            return delegate.getMaxHeight();
        }

        @Override
        public int getMinHeight() {
            return delegate.getMinHeight();
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
            return delegate.getBlock(x, y, z);
        }

        @Override
        public Biome getBiome(int x, int y, int z) {
            return delegate.getBiome(x, y, z);
        }

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Block material) {
            Objects.requireNonNull(material, "material");
            for (int x = xMin; x < xMax; x++) {
                for (int y = yMin; y < yMax; y++) {
                    for (int z = zMin; z < zMax; z++) {
                        setBlock(x, y, z, material);
                    }
                }
            }
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            Objects.requireNonNull(block, "block");
            IntVector position = new IntVector(x, y, z);
            if (shouldDefer.test(position, delegate)) {
                if (deferredWrites != null) {
                    deferredWrites.add(x, y, z, block);
                }
            } else {
                delegate.setBlock(x, y, z, block);
            }
        }

        @Override
        public void setBiome(int x, int y, int z, Biome biome) {
            if (deferredWrites != null) {
                delegate.setBiome(x, y, z, biome);
            }
        }

        @Override
        public void addEntity(EntityView entity) {
            if (deferredWrites != null) {
                delegate.addEntity(entity);
            }
        }
    }

    private record Applier(
            ChunkPatcher delegate,
            DeferredTasks deferredWrites,
            BiPredicate<Vector<Integer>, ChunkData> shouldDefer
    ) implements ChunkPatcher {

        private Applier {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(deferredWrites, "deferredWrites");
            Objects.requireNonNull(shouldDefer, "shouldDefer");
        }

        @Override
        public void patch(ChunkData targetChunk) {
            Objects.requireNonNull(targetChunk, "targetChunk");
            DeferredBlockWrites writes = deferredWrites.remove(coordinateOf(targetChunk));
            if (writes != null) {
                writes.applyTo(targetChunk);
            } else {
                delegate.patch(new ChunkDataProxy(targetChunk, null, shouldDefer.negate()));
            }
        }
    }

    private static final class DeferredBlockWrites {

        private static final int INITIAL_CAPACITY = 256;

        private int[] xCoordinates = new int[0];
        private int[] yCoordinates = new int[0];
        private int[] zCoordinates = new int[0];
        private Block[] blocks = new Block[0];
        private int size;

        private void add(int x, int y, int z, Block block) {
            ensureCapacity(size + 1);
            xCoordinates[size] = x;
            yCoordinates[size] = y;
            zCoordinates[size] = z;
            blocks[size] = block;
            size++;
        }

        private void applyTo(ChunkData targetChunk) {
            for (int index = 0; index < size; index++) {
                targetChunk.setBlock(
                        xCoordinates[index],
                        yCoordinates[index],
                        zCoordinates[index],
                        blocks[index]
                );
            }
        }

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity <= blocks.length) {
                return;
            }
            int newCapacity = blocks.length == 0
                    ? Math.max(requiredCapacity, INITIAL_CAPACITY)
                    : Math.max(requiredCapacity, blocks.length * 2);
            xCoordinates = Arrays.copyOf(xCoordinates, newCapacity);
            yCoordinates = Arrays.copyOf(yCoordinates, newCapacity);
            zCoordinates = Arrays.copyOf(zCoordinates, newCapacity);
            blocks = Arrays.copyOf(blocks, newCapacity);
        }
    }

    private static final class DeferredTasks {

        private final int capacity;
        private final Map<ChunkCoordinate, DeferredBlockWrites> batches = new LinkedHashMap<>();

        private DeferredTasks(int capacity) {
            if (capacity < 1) {
                throw new IllegalArgumentException("cacheCapacity must be at least 1");
            }
            this.capacity = capacity;
        }

        private synchronized void put(ChunkCoordinate coordinate, DeferredBlockWrites writes) {
            // Reinsertion represents a newer computation. Remove first so the coordinate moves to
            // the end of the insertion-ordered queue and its previous batch can be collected.
            batches.remove(coordinate);
            batches.put(coordinate, writes);
            if (batches.size() > capacity) {
                Iterator<ChunkCoordinate> coordinates = batches.keySet().iterator();
                coordinates.next();
                coordinates.remove();
            }
        }

        private synchronized DeferredBlockWrites remove(ChunkCoordinate coordinate) {
            return batches.remove(coordinate);
        }
    }
}
