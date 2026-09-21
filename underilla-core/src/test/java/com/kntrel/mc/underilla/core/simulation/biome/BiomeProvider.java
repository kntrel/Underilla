package com.kntrel.mc.underilla.core.simulation.biome;

import com.kntrel.mc.underilla.core.api.Biome;

/** Resolves the biome at a world position before the terrain-generation stages run. */
public interface BiomeProvider {

    /**
     * Returns the biome at the supplied world coordinates.
     *
     * <p>The Y coordinate is part of this API even though the initial implementation selects only
     * surface biomes. This allows a later implementation to select cave biomes without changing
     * callers.</p>
     */
    Biome getAt(int x, int y, int z);

    /** Creates the deterministic biome provider for a world seed. */
    static BiomeProvider fromSeed(long seed) {
        return new BiomeProviderImp(seed);
    }
}
