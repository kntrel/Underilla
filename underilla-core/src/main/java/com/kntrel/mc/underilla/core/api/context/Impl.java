package com.kntrel.mc.underilla.core.api.context;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.World;

/** Holds exactly the API values supplied by the caller. */
final class Impl {
    private Impl() {}

    record Chunks(ChunkView chunk, WorldScope source, WorldScope target) implements ChunkContext {}
    record Biomes(ChunkView chunk, BiomeScope source, BiomeScope target) implements BiomeContext {}
    record Blocks(ChunkView chunk, BlockScope source, BlockScope target) implements BlockContext {}

    record ChunkPosition(int x, int z) implements ChunkView {}

    record WorldSide(World world) implements WorldScope {}
    record BiomeSide(World world, Biome biome) implements BiomeScope {}
    record BlockSide(World world, Biome biome, Block block) implements BlockScope {}
}
