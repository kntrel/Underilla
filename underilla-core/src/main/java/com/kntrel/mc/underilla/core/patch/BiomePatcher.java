package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.BiomeData;

/** Selects or preserves the biome at one requested position. */
@FunctionalInterface
public interface BiomePatcher {

    /**
     * Patches the requested biome.
     *
     * @return {@code true} when this plan handled the position, or {@code false} when the platform should use its
     *         fallback biome provider
     */
    boolean patch(BiomeData biomeData);
}
