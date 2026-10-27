package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.World;
import java.util.Objects;

/** Read-only identity, seed, dimension, and vertical bounds of one world at evaluation time. */
public record WorldSnapshot(String name, ID dimension, int minHeight, int maxHeight, long seed) implements World {

    /** Creates metadata with the default seed of 0 when no seed is supplied. */
    public WorldSnapshot(String name, ID dimension, int minHeight, int maxHeight) {
        this(name, dimension, minHeight, maxHeight, 0L);
    }

    public WorldSnapshot {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimension, "dimension");
        if (maxHeight <= minHeight) {
            throw new IllegalArgumentException("maxHeight must be greater than minHeight");
        }
    }

    /** Captures world metadata, or null when the world is unavailable. */
    public static WorldSnapshot of(World world) {
        return world == null ? null : new WorldSnapshot(world.name(), world.dimension(),
                world.minHeight(), world.maxHeight(), world.seed());
    }
}
