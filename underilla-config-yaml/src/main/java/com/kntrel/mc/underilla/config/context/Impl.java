package com.kntrel.mc.underilla.config.context;

import com.kntrel.mc.underilla.config.entity.World;
import com.kntrel.mc.underilla.config.entity.Chunk;
import com.kntrel.mc.underilla.config.entity.Biome;
import com.kntrel.mc.underilla.config.entity.Block;
import com.kntrel.mc.underilla.config.entity.Structure;
import com.kntrel.mc.underilla.config.entity.Entity;

/** Immutable implementations of configuration contexts. */
interface Impl {
    record WorldContextValue(World world, Chunk chunk) implements WorldContext {}
    record BiomeContextValue(World world, Chunk chunk, Biome biome) implements BiomeContext {}
    record BlockContextValue(World world, Chunk chunk, Biome biome, Block block) implements BlockContext {}
    record StructureContextValue(World world, Chunk chunk, Biome biome, Structure structure) implements StructureContext {}
    record EntityContextValue(World world, Chunk chunk, Entity entity) implements EntityContext {}
    record DualWorldContextValue(WorldContext source, WorldContext target) implements DualWorldContext {}
    record DualBiomeContextValue(BiomeContext source, BiomeContext target) implements DualBiomeContext {}
    record DualBlockContextValue(BlockContext source, BlockContext target) implements DualBlockContext {}
}
