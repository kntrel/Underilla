package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.ID;
import java.util.Optional;

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

    boolean shouldPreserveBiome(ID biome);

    boolean preserveBiomesOnlyUnderSurface();

    boolean isSurfaceWorldOnlyBiome(ID biome);

    default boolean isInsideGenerationArea(int x, int z) {
        return x >= generationAreaMinX() && x < generationAreaMaxX()
                && z >= generationAreaMinZ() && z < generationAreaMaxZ();
    }

    boolean isIgnoredForSurfaceCalculation(ID block);

    boolean shouldKeepSurfaceBlockInCaves(ID block);

    Optional<ID> surfaceBlockReplacement(ID block);
}
