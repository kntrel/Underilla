package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.patch.BiomePatcher;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;

import java.util.Objects;

/**
 * Platform-neutral work and policies for one world-generation configuration.
 *
 * <p>The platform adapter maps its own generation callbacks to the patch phases. Biome and height resolution
 * are queries rather than lifecycle phases, so they are exposed separately.</p>
 */
public record WorldGenerationPlan(
        ChunkPatcher afterNoise,
        ChunkPatcher afterSurface,
        ChunkPatcher afterCarvers,
        ChunkPatcher afterFeatures,
        ChunkPatcher afterLoad,
        BiomePatcher biomePatch,
        GenerationFlags flags,
        Altimeter altimeter
) {

    public WorldGenerationPlan {
        Objects.requireNonNull(afterNoise, "afterNoise");
        Objects.requireNonNull(afterSurface, "afterSurface");
        Objects.requireNonNull(afterCarvers, "afterCarvers");
        Objects.requireNonNull(afterFeatures, "afterFeatures");
        Objects.requireNonNull(afterLoad, "afterLoad");
        Objects.requireNonNull(biomePatch, "biomePatch");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(altimeter, "altimeter");
    }

    public static WorldGenerationPlanBuilder build() { return new WorldGenerationPlanBuilder(); }

}
