package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.World;

/** One world's sampled biome, chunk coordinates, and metadata for a decision. */
public interface BiomeScope extends WorldScope {

    static BiomeScope of(Biome biome) {
        return of(null, biome);
    }

    static BiomeScope of(World world, Biome biome) {
        return of(WorldSnapshot.of(world), BiomeSnapshot.of(biome));
    }

    static BiomeScope of(WorldSnapshot world, BiomeSnapshot biome) {
        return of(world, null, biome);
    }

    static BiomeScope of(WorldSnapshot world, ChunkSnapshot chunk, BiomeSnapshot biome) {
        return new Impl.BiomeSide(world, chunk, biome);
    }

    /** {@return the captured biome, or null when unavailable during this decision} */
    BiomeSnapshot biome();
}
