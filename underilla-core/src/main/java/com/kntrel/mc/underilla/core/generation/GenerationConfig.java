package com.kntrel.mc.underilla.core.generation;

/** Platform-neutral configuration consumed by the generation and world-reading layers. */
public interface GenerationConfig {

    int cacheSize();

    int generationAreaMinX();

    int generationAreaMinY();

    int generationAreaMinZ();

    int generationAreaMaxX();

    int generationAreaMaxY();

    int generationAreaMaxZ();

    int maxHeightOfCaves();

    int mergeDepth();

    int adaptiveMaxMergeDepth();

    int adaptiveMinHiddenBlocksMergeDepth();

    boolean carversEnabled();

    boolean vanillaPopulationEnabled();

    boolean structuresEnabled();

    boolean surfaceBiomeUseTopYOnly();

    boolean shouldPreserveBiome(String biomeName);

    boolean preserveBiomesOnlyUnderSurface();

    boolean isSurfaceWorldOnlyBiome(String biomeName);

    default boolean isInsideGenerationArea(int x, int z) {
        return x >= generationAreaMinX() && x < generationAreaMaxX()
                && z >= generationAreaMinZ() && z < generationAreaMaxZ();
    }

    boolean isIgnoredForSurfaceCalculation(String blockName);

    boolean shouldKeepSurfaceBlockInCaves(String blockName);

    /** Returns the replacement block name, or {@code null} when the block should not be replaced. */
    String surfaceBlockReplacement(String blockName);
}
