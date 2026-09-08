package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.patch.BiomePatcher;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;
import java.util.function.Predicate;

/** Copies surface-world biomes while preserving selected generated biomes when requested. */
public final class SurfaceBiomePatcher implements BiomePatcher {

    private final WorldReader surfaceWorld;
    private final WorldMask worldMask;
    private final GenerationArea generationArea;
    private final int topY;
    private final boolean useTopYOnly;
    private final Predicate<ID> surfaceOnlyBiome;
    private final Predicate<ID> preservedGeneratedBiome;
    private final boolean preserveGeneratedBiomesOnlyUnderSurface;

    public SurfaceBiomePatcher(
            WorldReader surfaceWorld,
            WorldMask worldMask,
            GenerationArea generationArea,
            int topY,
            boolean useTopYOnly,
            Predicate<ID> surfaceOnlyBiome,
            Predicate<ID> preservedGeneratedBiome,
            boolean preserveGeneratedBiomesOnlyUnderSurface
    ) {
        this.surfaceWorld = Objects.requireNonNull(surfaceWorld, "surfaceWorld");
        this.worldMask = Objects.requireNonNull(worldMask, "worldMask");
        this.generationArea = Objects.requireNonNull(generationArea, "generationArea");
        this.topY = topY;
        this.useTopYOnly = useTopYOnly;
        this.surfaceOnlyBiome = Objects.requireNonNull(surfaceOnlyBiome, "surfaceOnlyBiome");
        this.preservedGeneratedBiome = Objects.requireNonNull(
                preservedGeneratedBiome, "preservedGeneratedBiome");
        this.preserveGeneratedBiomesOnlyUnderSurface = preserveGeneratedBiomesOnlyUnderSurface;
    }

    @Override
    public boolean patch(BiomeData biomeData) {
        Objects.requireNonNull(biomeData, "biomeData");
        if (!generationArea.contains(biomeData.getX(), biomeData.getZ())) {
            return false;
        }

        int referenceY = useTopYOnly ? topY : biomeData.getY();
        Biome referenceBiome = surfaceWorld
                .biomeAt(biomeData.getX(), referenceY, biomeData.getZ())
                .orElse(null);
        if (referenceBiome == null) {
            return false;
        }

        if (!surfaceOnlyBiome.test(referenceBiome.id())
                && preservedGeneratedBiome.test(biomeData.get().id())
                && isUnderSurface(biomeData)) {
            return true;
        }

        biomeData.set(referenceBiome);
        return true;
    }

    private boolean isUnderSurface(BiomeData biomeData) {
        if (!preserveGeneratedBiomesOnlyUnderSurface) {
            return true;
        }

        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        int cellX = biomeData.getBiomeX() * cellSize;
        int cellZ = biomeData.getBiomeZ() * cellSize;
        for (int x = cellX; x < cellX + cellSize; x++) {
            for (int z = cellZ; z < cellZ + cellSize; z++) {
                if (worldMask.contains(x, biomeData.getY(), z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
