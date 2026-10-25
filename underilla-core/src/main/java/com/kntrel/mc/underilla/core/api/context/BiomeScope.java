package com.kntrel.mc.underilla.core.api.context;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.World;

/** One world's sampled biome. */
public interface BiomeScope extends WorldScope {

    static BiomeScope of(Biome biome) {
        return of(null, biome);
    }

    static BiomeScope of(World world, Biome biome) {
        return new Impl.BiomeSide(world, biome);
    }

    /** {@return the supplied biome, or null when unavailable during this decision} */
    Biome biome();
}
