package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.World;

/** One world's block, sampled biome, chunk coordinates, and metadata for a decision. */
public interface BlockScope extends BiomeScope {

    static BlockScope of(Biome biome, Block block) {
        return of(null, biome, block);
    }

    static BlockScope of(World world, Biome biome, Block block) {
        return of(world, null, biome, block);
    }

    static BlockScope of(World world, ChunkSnapshot chunk, Biome biome, Block block) {
        BiomeSnapshot sample = biome instanceof BiomeSnapshot snapshot ? snapshot
                : block == null ? BiomeSnapshot.of(biome)
                : BiomeSnapshot.atBlock(biome, block.x(), block.y(), block.z());
        return of(WorldSnapshot.of(world), chunk, sample, BlockSnapshot.of(block));
    }

    static BlockScope of(WorldSnapshot world, BiomeSnapshot biome, BlockSnapshot block) {
        return of(world, null, biome, block);
    }

    static BlockScope of(WorldSnapshot world, ChunkSnapshot chunk, BiomeSnapshot biome, BlockSnapshot block) {
        return new Impl.BlockSide(world, chunk, biome, block);
    }

    /** {@return the captured block, or null when unavailable during this decision} */
    BlockSnapshot block();
}
