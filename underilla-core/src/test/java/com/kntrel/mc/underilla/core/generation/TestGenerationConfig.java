package com.kntrel.mc.underilla.core.generation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Mutable generation configuration intended for characterization fixtures. */
final class TestGenerationConfig implements GenerationConfig {

    private int minimumY;
    private int maximumY;
    private int maximumCaveY;
    private int mergeDepth;
    private int adaptiveMaximumDepth;
    private int adaptiveMinimumHiddenDepth;
    private final Set<String> surfaceOnlyBiomes = new HashSet<>();
    private final Set<String> ignoredSurfaceBlocks = new HashSet<>();
    private final Set<String> keptSurfaceBlocks = new HashSet<>();
    private final Map<String, String> surfaceReplacements = new HashMap<>();

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

    TestGenerationConfig preserveBiome(String biomeName) {
        surfaceOnlyBiomes.add(biomeName);
        return this;
    }

    TestGenerationConfig ignoreSurfaceBlock(String blockName) {
        ignoredSurfaceBlocks.add(blockName);
        return this;
    }

    TestGenerationConfig keepSurfaceBlock(String blockName) {
        keptSurfaceBlocks.add(blockName);
        return this;
    }

    TestGenerationConfig replaceSurfaceBlock(String blockName, String replacementName) {
        surfaceReplacements.put(blockName, replacementName);
        return this;
    }

    @Override
    public int cacheSize() { return 1; }

    @Override
    public int generationAreaMinY() { return minimumY; }

    @Override
    public int generationAreaMaxY() { return maximumY; }

    @Override
    public int maxHeightOfCaves() { return maximumCaveY; }

    @Override
    public int mergeDepth() { return mergeDepth; }

    @Override
    public int adaptiveMaxMergeDepth() { return adaptiveMaximumDepth; }

    @Override
    public int adaptiveMinHiddenBlocksMergeDepth() { return adaptiveMinimumHiddenDepth; }

    @Override
    public boolean carversEnabled() { return true; }

    @Override
    public boolean vanillaPopulationEnabled() { return true; }

    @Override
    public boolean structuresEnabled() { return true; }

    @Override
    public boolean isSurfaceWorldOnlyBiome(String biomeName) { return surfaceOnlyBiomes.contains(biomeName); }

    @Override
    public boolean isIgnoredForSurfaceCalculation(String blockName) {
        return ignoredSurfaceBlocks.contains(blockName);
    }

    @Override
    public boolean shouldKeepSurfaceBlockInCaves(String blockName) {
        return keptSurfaceBlocks.contains(blockName);
    }

    @Override
    public String surfaceBlockReplacement(String blockName) { return surfaceReplacements.get(blockName); }
}
