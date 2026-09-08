package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.GenerationConstants;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A bounded, chunk-bucketed LRU cache around another world mask. */
public final class CachedWorldMask implements WorldMask {

    private static final byte UNKNOWN = 0;
    private static final byte OUTSIDE = 1;
    private static final byte INSIDE = 2;

    private final WorldMask delegate;
    private final int maximumCapacity;
    private final Map<ChunkCoordinate, ChunkBucket> chunkBuckets = new LinkedHashMap<>(16, 0.75f, true);

    public CachedWorldMask(WorldMask delegate, int maximumCapacity) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maximumCapacity < 1) {
            throw new IllegalArgumentException("maximumCapacity must be at least 1");
        }
        this.maximumCapacity = maximumCapacity;
    }

    @Override
    public boolean contains(int globalX, int y, int globalZ) {
        int chunkX = Math.floorDiv(globalX, GenerationConstants.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(globalZ, GenerationConstants.CHUNK_SIZE);
        ChunkBucket bucket = bucketAt(chunkX, chunkZ);
        return bucket.contains(delegate, globalX, y, globalZ);
    }

    private ChunkBucket bucketAt(int chunkX, int chunkZ) {
        ChunkCoordinate chunk = new ChunkCoordinate(chunkX, chunkZ);
        synchronized (chunkBuckets) {
            ChunkBucket bucket = chunkBuckets.get(chunk);
            if (bucket == null) {
                bucket = new ChunkBucket();
                chunkBuckets.put(chunk, bucket);
                evictLeastRecentlyUsedBucket();
            }
            return bucket;
        }
    }

    private void evictLeastRecentlyUsedBucket() {
        if (chunkBuckets.size() <= maximumCapacity) {
            return;
        }
        Iterator<ChunkCoordinate> iterator = chunkBuckets.keySet().iterator();
        iterator.next();
        iterator.remove();
    }

    private static int index(int localX, int localY, int localZ) {
        int size = GenerationConstants.CHUNK_SIZE;
        return (localY * size + localZ) * size + localX;
    }

    private record ChunkCoordinate(int x, int z) {}

    private static final class ChunkBucket {

        private final Map<Integer, byte[]> sections = new HashMap<>();

        private synchronized boolean contains(
                WorldMask delegate,
                int globalX,
                int y,
                int globalZ
        ) {
            int sectionY = Math.floorDiv(y, GenerationConstants.CHUNK_SIZE);
            byte[] section = sections.computeIfAbsent(
                    sectionY,
                    _ -> new byte[GenerationConstants.CHUNK_SIZE
                            * GenerationConstants.CHUNK_SIZE
                            * GenerationConstants.CHUNK_SIZE]
            );
            int valueIndex = index(
                    Math.floorMod(globalX, GenerationConstants.CHUNK_SIZE),
                    Math.floorMod(y, GenerationConstants.CHUNK_SIZE),
                    Math.floorMod(globalZ, GenerationConstants.CHUNK_SIZE)
            );
            byte cached = section[valueIndex];
            if (cached == UNKNOWN) {
                cached = delegate.contains(globalX, y, globalZ) ? INSIDE : OUTSIDE;
                section[valueIndex] = cached;
            }
            return cached == INSIDE;
        }
    }
}
