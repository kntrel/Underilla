package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.GenerationConstants;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A bounded, chunk-bucketed LRU cache around another boundary. */
public final class CachedBoundary implements Boundary {

    private final Boundary delegate;
    private final int maximumCapacity;
    private final Map<ChunkCoordinate, int[]> chunkBuckets = new LinkedHashMap<>(16, 0.75f, true);

    public CachedBoundary(Boundary delegate, int maximumCapacity) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maximumCapacity < 1) {
            throw new IllegalArgumentException("maximumCapacity must be at least 1");
        }
        this.maximumCapacity = maximumCapacity;
    }

    @Override
    public int at(int globalX, int globalZ) {
        int chunkX = Math.floorDiv(globalX, GenerationConstants.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(globalZ, GenerationConstants.CHUNK_SIZE);
        ChunkCoordinate chunk = new ChunkCoordinate(chunkX, chunkZ);

        int[] bucket;
        synchronized (chunkBuckets) {
            bucket = chunkBuckets.get(chunk);
        }
        if (bucket == null) {
            int[] calculatedBucket = calculateBucket(chunkX, chunkZ);
            synchronized (chunkBuckets) {
                bucket = chunkBuckets.get(chunk);
                if (bucket == null) {
                    bucket = calculatedBucket;
                    chunkBuckets.put(chunk, bucket);
                    evictLeastRecentlyUsedBucket();
                }
            }
        }

        int localX = Math.floorMod(globalX, GenerationConstants.CHUNK_SIZE);
        int localZ = Math.floorMod(globalZ, GenerationConstants.CHUNK_SIZE);
        return bucket[index(localX, localZ)];
    }

    private int[] calculateBucket(int chunkX, int chunkZ) {
        int[] bucket = new int[GenerationConstants.CHUNK_SIZE * GenerationConstants.CHUNK_SIZE];
        int originX = chunkX * GenerationConstants.CHUNK_SIZE;
        int originZ = chunkZ * GenerationConstants.CHUNK_SIZE;
        for (int localX = 0; localX < GenerationConstants.CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < GenerationConstants.CHUNK_SIZE; localZ++) {
                bucket[index(localX, localZ)] = delegate.at(originX + localX, originZ + localZ);
            }
        }
        return bucket;
    }

    private void evictLeastRecentlyUsedBucket() {
        if (chunkBuckets.size() <= maximumCapacity) {
            return;
        }
        Iterator<ChunkCoordinate> iterator = chunkBuckets.keySet().iterator();
        iterator.next();
        iterator.remove();
    }

    private static int index(int localX, int localZ) {
        return localX * GenerationConstants.CHUNK_SIZE + localZ;
    }

    private record ChunkCoordinate(int x, int z) {}
}
