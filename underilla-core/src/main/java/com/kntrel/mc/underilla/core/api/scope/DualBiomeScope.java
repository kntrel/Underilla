package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.World;

/** Source and target biome scopes for a decision. */
public interface DualBiomeScope extends DualWorldScope {

    static DualBiomeScope of(BiomeScope source, BiomeScope target) {
        return new Impl.DualBiomes(source, target);
    }

    static DualBiomeScope of(ChunkSnapshot chunk, Biome sourceBiome, Biome targetBiome) {
        return of(chunk, null, sourceBiome, null, targetBiome);
    }

    static DualBiomeScope of(
            ChunkSnapshot chunk, World sourceWorld, Biome sourceBiome, World targetWorld, Biome targetBiome
    ) {
        return of(BiomeScope.of(WorldSnapshot.of(sourceWorld), chunk, BiomeSnapshot.of(sourceBiome)),
                BiomeScope.of(WorldSnapshot.of(targetWorld), chunk, BiomeSnapshot.of(targetBiome)));
    }

    @Override
    BiomeScope source();

    @Override
    BiomeScope target();
}
