package com.kntrel.mc.underilla.core.api.scope;

/** Record implementations of individual and dual scopes for decision rules. */
final class Impl {
    private Impl() {}

    record WorldSide(WorldSnapshot world, ChunkSnapshot chunk) implements WorldScope {}
    record BiomeSide(WorldSnapshot world, ChunkSnapshot chunk, BiomeSnapshot biome) implements BiomeScope {}
    record BlockSide(WorldSnapshot world, ChunkSnapshot chunk, BiomeSnapshot biome, BlockSnapshot block) implements BlockScope {}
    record StructureSide(WorldSnapshot world, ChunkSnapshot chunk, BiomeSnapshot biome, StructureSnapshot structure)
            implements StructureScope {}
    record EntitySide(WorldSnapshot world, ChunkSnapshot chunk, EntitySnapshot entity) implements EntityScope {}

    record DualWorlds(WorldScope source, WorldScope target) implements DualWorldScope {}
    record DualBiomes(BiomeScope source, BiomeScope target) implements DualBiomeScope {}
    record DualBlocks(BlockScope source, BlockScope target) implements DualBlockScope {}
}
