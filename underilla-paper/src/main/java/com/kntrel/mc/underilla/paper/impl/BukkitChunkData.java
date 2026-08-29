package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import org.bukkit.block.data.BlockData;

public class BukkitChunkData implements ChunkData {

    // FIELDS
    private final org.bukkit.generator.ChunkGenerator.ChunkData chunkData;
    private final int chunkX;
    private final int chunkZ;


    // CONSTRUCTORS
    public BukkitChunkData(org.bukkit.generator.ChunkGenerator.ChunkData chunkData, int chunkX, int chunkZ) {
        this.chunkData = chunkData;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }


    @Override
    public int getMinHeight() { return this.chunkData.getMinHeight(); }
    @Override
    public int getChunkX() { return chunkX; }
    @Override
    public int getChunkZ() { return chunkZ; }

    @Override
    public Block getBlock(int x, int y, int z) {
        BlockData data = this.chunkData.getBlockData(x, y, z);
        return new BukkitBlock(data);
    }

    @Override
    public int getMaxHeight() { return this.chunkData.getMaxHeight(); }

    @Override
    public com.kntrel.mc.underilla.core.api.Biome getBiome(int x, int y, int z) {
        return new BukkitBiome(this.chunkData.getBiome(x, y, z).getKey());
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Block block) {
        if (!(block instanceof BukkitBlock bukkitBlock)) {
            return;
        }
        this.chunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, bukkitBlock.getBlockData());
    }

    @Override
    public void setBlock(int x, int y, int z, Block block) {
        if (!(block instanceof BukkitBlock bukkitBlock)) {
            return;
        }


        this.chunkData.setBlock(x, y, z, bukkitBlock.getBlockData());

    }

    @Override
    public void setBiome(int x, int y, int z, com.kntrel.mc.underilla.core.api.Biome biome) {
        // No need to set biome for chunk. It's done by the generator.
    }
}
