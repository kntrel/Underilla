package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.ID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Mutable generation configuration intended for characterization fixtures. */
final class TestGenerationConfig implements GenerationConfig {

    private int minimumY;
    private int maximumY;
    private int minimumX = Integer.MIN_VALUE;
    private int minimumZ = Integer.MIN_VALUE;
    private int maximumX = Integer.MAX_VALUE;
    private int maximumZ = Integer.MAX_VALUE;
    private int maximumCaveY;
    private int mergeDepth;
    private int adaptiveMaximumDepth;
    private int adaptiveMinimumHiddenDepth;
    private boolean preserveGeneratedBiomesOnlyUnderSurface;
    private final Set<ID> surfaceOnlyBiomes = new HashSet<>();
    private final Set<ID> preservedGeneratedBiomes = new HashSet<>();
    private final Set<ID> ignoredSurfaceBlocks = new HashSet<>();
    private final Set<ID> keptSurfaceBlocks = new HashSet<>();
    private final Map<ID, ID> surfaceReplacements = new HashMap<>();

    TestGenerationConfig(int minimumY, int maximumY) {
        this.minimumY = minimumY;
        this.maximumY = maximumY;
        this.maximumCaveY = maximumY;
    }

    TestGenerationConfig maximumCaveY(int value) {
        maximumCaveY = value;
        return this;
    }

    TestGenerationConfig generationArea(int minimumX, int minimumZ, int maximumX, int maximumZ) {
        this.minimumX = minimumX;
        this.minimumZ = minimumZ;
        this.maximumX = maximumX;
        this.maximumZ = maximumZ;
        return this;
    }

    TestGenerationConfig preserveGeneratedBiome(String biomeName) {
        preservedGeneratedBiomes.add(ID.of(biomeName));
        return this;
    }

    TestGenerationConfig preserveGeneratedBiomesOnlyUnderSurface() {
        preserveGeneratedBiomesOnlyUnderSurface = true;
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
        surfaceOnlyBiomes.add(ID.of(biomeName));
        return this;
    }

    TestGenerationConfig ignoreSurfaceBlock(String blockName) {
        ignoredSurfaceBlocks.add(ID.of(blockName));
        return this;
    }

    TestGenerationConfig keepSurfaceBlock(String blockName) {
        keptSurfaceBlocks.add(ID.of(blockName));
        return this;
    }

    TestGenerationConfig replaceSurfaceBlock(String blockName, String replacementName) {
        surfaceReplacements.put(ID.of(blockName), ID.of(replacementName));
        return this;
    }

    @Override
    public int cacheSize() { return 1; }

    @Override
    public int generationAreaMinX() { return minimumX; }

    @Override
    public int generationAreaMinY() { return minimumY; }

    @Override
    public int generationAreaMinZ() { return minimumZ; }

    @Override
    public int generationAreaMaxX() { return maximumX; }

    @Override
    public int generationAreaMaxY() { return maximumY; }

    @Override
    public int generationAreaMaxZ() { return maximumZ; }

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
    public boolean surfaceBiomeUseTopYOnly() { return false; }

    @Override
    public boolean shouldPreserveBiome(ID biome) { return preservedGeneratedBiomes.contains(biome); }

    @Override
    public boolean preserveBiomesOnlyUnderSurface() { return preserveGeneratedBiomesOnlyUnderSurface; }

    @Override
    public boolean isSurfaceWorldOnlyBiome(ID biome) { return surfaceOnlyBiomes.contains(biome); }

    @Override
    public boolean isIgnoredForSurfaceCalculation(ID block) {
        return ignoredSurfaceBlocks.contains(block);
    }

    @Override
    public boolean shouldKeepSurfaceBlockInCaves(ID block) {
        return keptSurfaceBlocks.contains(block);
    }

    @Override
    public Optional<ID> surfaceBlockReplacement(ID block) { return Optional.ofNullable(surfaceReplacements.get(block)); }
}
