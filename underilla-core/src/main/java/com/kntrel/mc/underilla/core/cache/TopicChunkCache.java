package com.kntrel.mc.underilla.core.cache;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A view of one topic in a shared {@link ChunkCache}.
 * Owns no storage or eviction policy; all views share the backing cache's chunk capacity
 * and LRU order. Coordinates are chunk coordinates, not block coordinates.
 */
public final class TopicChunkCache<T> {

    private final ChunkCache cache;
    private final Class<T> topic;

    public TopicChunkCache(ChunkCache cache, Class<T> topic) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.topic = Objects.requireNonNull(topic, "topic");
    }

    public Optional<T> get(int chunkX, int chunkZ) {
        return cache.get(chunkX, chunkZ, topic);
    }

    /** Stores a value whose exact runtime class matches this view's topic. */
    public void put(int chunkX, int chunkZ, T value) {
        Objects.requireNonNull(value, "value");
        if (value.getClass() != topic) {
            throw new IllegalArgumentException("value's runtime class must match the topic");
        }
        cache.put(chunkX, chunkZ, value);
    }

    public T getOrCompute(int chunkX, int chunkZ, Supplier<? extends T> supplier) {
        return cache.getOrCompute(chunkX, chunkZ, topic, supplier);
    }

    public Optional<T> take(int chunkX, int chunkZ) {
        return cache.take(chunkX, chunkZ, topic);
    }
}
