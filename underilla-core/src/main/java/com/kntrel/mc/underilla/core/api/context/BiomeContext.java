package com.kntrel.mc.underilla.core.api.context;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.World;

/** Pairs source and target biomes at shared chunk coordinates. */
public interface BiomeContext extends ChunkContext {

    static BiomeContext of(ChunkView chunk, Biome sourceBiome, Biome targetBiome) {
        return of(chunk, null, sourceBiome, null, targetBiome);
    }

    static BiomeContext of(
            ChunkView chunk, World sourceWorld, Biome sourceBiome, World targetWorld, Biome targetBiome
    ) {
        return new Impl.Biomes(chunk, BiomeScope.of(sourceWorld, sourceBiome),
                BiomeScope.of(targetWorld, targetBiome));
    }

    @Override
    BiomeScope source();

    @Override
    BiomeScope target();
}
