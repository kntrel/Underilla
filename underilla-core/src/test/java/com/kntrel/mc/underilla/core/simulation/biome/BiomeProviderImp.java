package com.kntrel.mc.underilla.core.simulation.biome;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.simulation.noise.FastNoiseLite;
import java.util.List;

/** Seeded surface-biome map. Cave-biome selection will be layered onto this later. */
final class BiomeProviderImp implements BiomeProvider {

    private static final int CAVE_BIOME_MAXIMUM_Y = 64;
    private static final float CAVE_BIOME_THRESHOLD = 0.2f;
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
    private static final List<Biome> CAVE_BIOMES = List.of(
            new TestBiome("minecraft:deep_dark"),
            new TestBiome("minecraft:dripstone_caves"),
            new TestBiome("minecraft:lush_caves"));

    private final FastNoiseLite surfaceNoise;
    private final FastNoiseLite caveNoise;
    private final FastNoiseLite caveBiomeNoise;

    BiomeProviderImp(long seed) {
        surfaceNoise = new FastNoiseLite(Long.hashCode(seed ^ 0xD1B54A32D192ED03L));
        surfaceNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        surfaceNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        surfaceNoise.SetFractalOctaves(3);
        surfaceNoise.SetFrequency(0.0015f);

        caveNoise = threeDimensionalNoise(seed ^ 0x94D049BB133111EBL, 0.02f);
        caveBiomeNoise = threeDimensionalNoise(seed ^ 0xBF58476D1CE4E5B9L, 0.01f);
    }

    @Override
    public Biome getAt(int x, int y, int z) {
        Biome surfaceBiome = biomeForNoise(SURFACE_BIOMES, surfaceNoise.GetNoise(x, z));
        if (y >= CAVE_BIOME_MAXIMUM_Y || caveNoise.GetNoise(x, y, z) < CAVE_BIOME_THRESHOLD) {
            return surfaceBiome;
        }
        return biomeForNoise(CAVE_BIOMES, caveBiomeNoise.GetNoise(x, y, z));
    }

    private static FastNoiseLite threeDimensionalNoise(long seed, float frequency) {
        FastNoiseLite noise = new FastNoiseLite(Long.hashCode(seed));
        noise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(3);
        noise.SetFrequency(frequency);
        return noise;
    }

    private static Biome biomeForNoise(List<Biome> biomes, float noise) {
        float normalized = (noise + 1.0f) * 0.5f;
        int index = Math.clamp((int) (normalized * biomes.size()), 0, biomes.size() - 1);
        return biomes.get(index);
    }
}
