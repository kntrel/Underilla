package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.ChunkData;
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
        ChunkCoverage coverage,
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
        Objects.requireNonNull(coverage, "coverage");
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

    /**
     * Applies the after-noise patcher when this plan covers the target chunk.
     *
     * @return {@code true} when the patcher ran, or {@code false} when the chunk was not covered
     */
    public boolean tryAfterNoise(ChunkData targetChunk) {
        return tryPatch(afterNoise, targetChunk);
    }

    /**
     * Applies the after-surface patcher when this plan covers the target chunk.
     *
     * @return {@code true} when the patcher ran, or {@code false} when the chunk was not covered
     */
    public boolean tryAfterSurface(ChunkData targetChunk) {
        return tryPatch(afterSurface, targetChunk);
    }

    /**
     * Applies the after-carvers patcher when this plan covers the target chunk.
     *
     * @return {@code true} when the patcher ran, or {@code false} when the chunk was not covered
     */
    public boolean tryAfterCarvers(ChunkData targetChunk) {
        return tryPatch(afterCarvers, targetChunk);
    }

    /**
     * Applies the after-features patcher when this plan covers the target chunk.
     *
     * @return {@code true} when the patcher ran, or {@code false} when the chunk was not covered
     */
    public boolean tryAfterFeatures(ChunkData targetChunk) {
        return tryPatch(afterFeatures, targetChunk);
    }

    /**
     * Applies the after-load patcher when this plan covers the target chunk.
     *
     * @return {@code true} when the patcher ran, or {@code false} when the chunk was not covered
     */
    public boolean tryAfterLoad(ChunkData targetChunk) {
        return tryPatch(afterLoad, targetChunk);
    }

    private boolean tryPatch(ChunkPatcher patcher, ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        if (!coverage.covers(targetChunk.getChunkX(), targetChunk.getChunkZ())) {
            return false;
        }
        patcher.patch(targetChunk);
        return true;
    }

}
