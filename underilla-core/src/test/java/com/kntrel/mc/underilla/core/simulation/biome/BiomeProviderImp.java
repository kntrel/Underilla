package com.kntrel.mc.underilla.core.simulation.biome;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.simulation.noise.FastNoiseLite;
import java.util.List;

/** Seeded surface-biome map. Cave-biome selection will be layered onto this later. */
final class BiomeProviderImp implements BiomeProvider {

    private static final List<Biome> SURFACE_BIOMES = List.of(
            new TestBiome("minecraft:plains"),
            new TestBiome("minecraft:sunflower_plains"),
            new TestBiome("minecraft:forest"),
            new TestBiome("minecraft:flower_forest"),
            new TestBiome("minecraft:birch_forest"),
            new TestBiome("minecraft:dark_forest"),
            new TestBiome("minecraft:taiga"),
            new TestBiome("minecraft:snowy_taiga"),
            new TestBiome("minecraft:old_growth_pine_taiga"),
            new TestBiome("minecraft:old_growth_spruce_taiga"),
            new TestBiome("minecraft:desert"),
            new TestBiome("minecraft:savanna"),
            new TestBiome("minecraft:savanna_plateau"),
            new TestBiome("minecraft:jungle"),
            new TestBiome("minecraft:sparse_jungle"),
            new TestBiome("minecraft:bamboo_jungle"),
            new TestBiome("minecraft:swamp"),
            new TestBiome("minecraft:mangrove_swamp"),
            new TestBiome("minecraft:badlands"),
            new TestBiome("minecraft:eroded_badlands"),
            new TestBiome("minecraft:wooded_badlands"),
            new TestBiome("minecraft:windswept_hills"),
            new TestBiome("minecraft:meadow"),
            new TestBiome("minecraft:grove"),
            new TestBiome("minecraft:snowy_slopes"),
            new TestBiome("minecraft:frozen_peaks"),
            new TestBiome("minecraft:jagged_peaks"),
            new TestBiome("minecraft:stony_peaks"),
            new TestBiome("minecraft:river"),
            new TestBiome("minecraft:frozen_river"),
            new TestBiome("minecraft:beach"),
            new TestBiome("minecraft:snowy_beach"),
            new TestBiome("minecraft:stony_shore"),
            new TestBiome("minecraft:ocean"),
            new TestBiome("minecraft:deep_ocean"),
            new TestBiome("minecraft:warm_ocean"),
            new TestBiome("minecraft:lukewarm_ocean"),
            new TestBiome("minecraft:cold_ocean"),
            new TestBiome("minecraft:frozen_ocean"));

    private final FastNoiseLite surfaceNoise;

    BiomeProviderImp(long seed) {
        surfaceNoise = new FastNoiseLite(Long.hashCode(seed ^ 0xD1B54A32D192ED03L));
        surfaceNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        surfaceNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        surfaceNoise.SetFractalOctaves(3);
        surfaceNoise.SetFrequency(0.0015f);
    }

    @Override
    public Biome getAt(int x, int y, int z) {
        float normalized = (surfaceNoise.GetNoise(x, z) + 1.0f) * 0.5f;
        int index = Math.clamp((int) (normalized * SURFACE_BIOMES.size()), 0, SURFACE_BIOMES.size() - 1);
        return SURFACE_BIOMES.get(index);
    }
}
