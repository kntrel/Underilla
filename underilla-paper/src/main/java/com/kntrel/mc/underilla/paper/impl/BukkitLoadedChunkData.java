package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.reader.EntityView;
import com.kntrel.mc.underilla.core.vector.VectorIterable;
import com.kntrel.mc.underilla.paper.Underilla;
import java.util.Objects;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/** A mutable core chunk view used once a newly generated Bukkit chunk is live. */
public final class BukkitLoadedChunkData implements ChunkData {

    private final Chunk chunk;
    private final World world;
    private final int absoluteX;
    private final int absoluteZ;

    public BukkitLoadedChunkData(Chunk chunk) {
        this.chunk = Objects.requireNonNull(chunk, "chunk");
        this.world = chunk.getWorld();
        this.absoluteX = chunk.getX() * Underilla.CHUNK_SIZE;
        this.absoluteZ = chunk.getZ() * Underilla.CHUNK_SIZE;
    }

    @Override
    public int getMaxHeight() { return world.getMaxHeight(); }

    @Override
    public int getMinHeight() { return world.getMinHeight(); }

    @Override
    public int getChunkX() { return chunk.getX(); }

    @Override
    public int getChunkZ() { return chunk.getZ(); }

    @Override
    public Block getBlock(int x, int y, int z) {
        return new BukkitBlock(chunk.getBlock(x, y, z).getBlockData());
    }

    @Override
    public Biome getBiome(int x, int y, int z) {
        return new BukkitBiome(world.getBiome(absoluteX + x, y, absoluteZ + z).getKey());
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Block block) {
        new VectorIterable(xMin, xMax, yMin, yMax, zMin, zMax).forEach(position -> setBlock(position, block));
    }

    @Override
    public void setBlock(int x, int y, int z, Block block) {
        if (block instanceof BukkitBlock bukkitBlock) {
            BlockData data = bukkitBlock.getBlockData();
            chunk.getBlock(x, y, z).setBlockData(data, false);
        }
    }

    @Override
    public void setBiome(int x, int y, int z, Biome biome) {
        if (!(biome instanceof BukkitBiome bukkitBiome)) {
            return;
        }
        org.bukkit.block.Biome selectedBiome = bukkitBiome.getBiome();
        if (Underilla.getInstance().hasEndBiomeTransformer()) {
            selectedBiome = Underilla.getInstance().getEndBiomeTransformer().apply(selectedBiome);
        }
        world.setBiome(absoluteX + x, y, absoluteZ + z, selectedBiome);
    }

    @Override
    public void addEntity(EntityView entity) {
        throw new UnsupportedOperationException("Loaded chunk data cannot add entities");
    }
}
