package com.kntrel.mc.underilla.core.generation;

/** Platform-neutral configuration consumed by the generation and world-reading layers. */
public interface GenerationConfig {

    int cacheSize();

    int generationAreaMinY();

    int generationAreaMaxY();

    int maxHeightOfCaves();

    int mergeDepth();

    int adaptiveMaxMergeDepth();

    int adaptiveMinHiddenBlocksMergeDepth();

    boolean carversEnabled();

    boolean vanillaPopulationEnabled();

    boolean structuresEnabled();

    boolean isSurfaceWorldOnlyBiome(String biomeName);

    boolean isIgnoredForSurfaceCalculation(String blockName);

    boolean shouldKeepSurfaceBlockInCaves(String blockName);

    /** Returns the replacement block name, or {@code null} when the block should not be replaced. */
    String surfaceBlockReplacement(String blockName);
}
