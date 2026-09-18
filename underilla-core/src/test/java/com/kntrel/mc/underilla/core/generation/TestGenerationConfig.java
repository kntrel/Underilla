package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.ID;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Mutable generation configuration intended for characterization fixtures. */
final class TestGenerationConfig {

    private int minimumY;
    private int maximumY;
    private int maximumCaveY;
    private int mergeDepth;
    private int adaptiveMaximumDepth;
    private int adaptiveMinimumHiddenDepth;
    private final Set<ID> ignoredSurfaceBlocks = new HashSet<>();

    TestGenerationConfig(int minimumY, int maximumY) {
        this.minimumY = minimumY;
        this.maximumY = maximumY;
        this.maximumCaveY = maximumY;
    }

    TestGenerationConfig maximumCaveY(int value) {
        maximumCaveY = value;
        return this;
    }

    TestGenerationConfig mergeDepth(int value) {
        mergeDepth = value;
        return this;
    }

    TestGenerationConfig adaptiveMaximumDepth(int value) {
        adaptiveMaximumDepth = value;
        return this;
    }

    TestGenerationConfig adaptiveMinimumHiddenDepth(int value) {
        adaptiveMinimumHiddenDepth = value;
        return this;
    }

    TestGenerationConfig ignoreSurfaceBlock(String blockName) {
        ignoredSurfaceBlocks.add(ID.of(blockName));
        return this;
    }

    public int cacheSize() { return 1; }

    public int generationAreaMinY() { return minimumY; }

    public int generationAreaMaxY() { return maximumY; }

    public int maxHeightOfCaves() { return maximumCaveY; }

    public int mergeDepth() { return mergeDepth; }

    public int adaptiveMaxMergeDepth() { return adaptiveMaximumDepth; }

    public int adaptiveMinHiddenBlocksMergeDepth() { return adaptiveMinimumHiddenDepth; }

    public boolean isSurfaceWorldOnlyBiome(ID biome) { return false; }

    public boolean isIgnoredForSurfaceCalculation(ID block) {
        return ignoredSurfaceBlocks.contains(block);
    }

    public boolean shouldKeepSurfaceBlockInCaves(ID block) {
        return false;
    }

    public Optional<ID> surfaceBlockReplacement(ID block) { return Optional.empty(); }
}
