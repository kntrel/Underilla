package com.kntrel.mc.underilla.core.cache;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A thread-safe, chunk-bounded LRU cache with one value per topic class per chunk.
 * Coordinates are chunk coordinates, not block coordinates. Each instance should belong to
 * one world/generation plan so that coordinates and topic classes uniquely identify values.
 *
 * <p>Accessing any topic promotes its whole chunk, even if that topic is absent. Eviction
 * removes all topics together. Taking a topic leaves the chunk and its other topics resident.
 * Empty chunks count towards capacity until evicted.</p>
 *
 * <p>Topic operations are serialized within each chunk; different chunks can load concurrently.
 * Suppliers run outside the LRU lock and must not call back into this cache. Eviction does not
 * cancel in-flight operations: they finish against their original bucket without reinserting it.
 * A subsequent access to an evicted chunk can therefore start a new calculation. Returned
 * mutable values require their own synchronization.</p>
 */
public final class ChunkCache {

    private final int maximumCapacity;
    private final Map<ChunkCoordinate, ChunkBucket> chunks = new LinkedHashMap<>(16, 0.75f, true);

    /** @param maximumCapacity maximum number of resident chunks, at least one */
    public ChunkCache(int maximumCapacity) {
        if (maximumCapacity < 1) {
            throw new IllegalArgumentException("maximumCapacity must be at least 1");
        }
        this.maximumCapacity = maximumCapacity;
    }

    /** Looks up a topic without creating a chunk on a miss. */
    public <T> Optional<T> get(int chunkX, int chunkZ, Class<T> topic) {
        Objects.requireNonNull(topic, "topic");
        ChunkBucket bucket = bucketAt(chunkX, chunkZ, false);
        return bucket == null ? Optional.empty() : bucket.get(topic);
    }

    /** Stores or replaces a non-null value under its exact runtime class. */
    public void put(int chunkX, int chunkZ, Object value) {
        Objects.requireNonNull(value, "value");
        bucketAt(chunkX, chunkZ, true).put(value);
    }

    /**
     * Returns the resident value or computes it once within the current chunk bucket.
     * Null results and failures are not cached, allowing a later call to retry.
     */
    public <T> T getOrCompute(int chunkX, int chunkZ, Class<T> topic, Supplier<? extends T> supplier) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(supplier, "supplier");
        return bucketAt(chunkX, chunkZ, true).getOrCompute(topic, supplier);
    }

    /** Removes and returns only the requested topic, without creating a chunk on a miss. */
    public <T> Optional<T> take(int chunkX, int chunkZ, Class<T> topic) {
        Objects.requireNonNull(topic, "topic");
        ChunkBucket bucket = bucketAt(chunkX, chunkZ, false);
        return bucket == null ? Optional.empty() : bucket.take(topic);
    }

    /** Returns a view of this cache scoped to the provided topic*/
    public <T> TopicChunkCache<T> topicView(Class<T> topic) {
        return new TopicChunkCache<>(this, topic);
    }

    private ChunkBucket bucketAt(int chunkX, int chunkZ, boolean create) {
        ChunkCoordinate coordinate = new ChunkCoordinate(chunkX, chunkZ);
        synchronized (chunks) {
            ChunkBucket bucket = chunks.get(coordinate);
            if (bucket == null && create) {
                bucket = new ChunkBucket();
                chunks.put(coordinate, bucket);
                if (chunks.size() > maximumCapacity) {
                    var iterator = chunks.keySet().iterator();
                    iterator.next();
                    iterator.remove();
                }
            }
            return bucket;
        }
    }

    private record ChunkCoordinate(int x, int z) {}

    private static final class ChunkBucket {

        private final Map<Class<?>, Object> topics = new HashMap<>();

        private synchronized <T> Optional<T> get(Class<T> topic) {
            return Optional.ofNullable(topic.cast(topics.get(topic)));
        }

        private synchronized void put(Object value) {
            topics.put(value.getClass(), value);
        }

        private synchronized <T> T getOrCompute(Class<T> topic, Supplier<? extends T> supplier) {
            T value = topic.cast(topics.get(topic));
            if (value == null) {
                value = topic.cast(Objects.requireNonNull(supplier.get(), "supplier result"));
                topics.put(topic, value);
            }
            return value;
        }

        private synchronized <T> Optional<T> take(Class<T> topic) {
            return Optional.ofNullable(topic.cast(topics.remove(topic)));
        }
    }
}
