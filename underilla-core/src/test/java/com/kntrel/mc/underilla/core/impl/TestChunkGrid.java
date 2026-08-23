package com.kntrel.mc.underilla.core.impl;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import java.util.Arrays;
import java.util.Objects;

/**
 * Dense 3D block and biome grid representing one chunk.
 *
 * <p>X and Z are local chunk coordinates. Y uses world coordinates.</p>
 */
public final class TestChunkGrid implements ChunkData {

    private final int chunkX;
    private final int chunkZ;
    private final int minimumY;
    private final int maximumY;
    private final Block[][][] blocks;
    private final Biome[][][] biomes;

    public TestChunkGrid(int chunkX, int chunkZ, int minimumY, int maximumY, Block defaultBlock, Biome defaultBiome) {
        if (maximumY <= minimumY) {
            throw new IllegalArgumentException("maximumY must be greater than minimumY");
        }
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minimumY = minimumY;
        this.maximumY = maximumY;
        this.blocks = new Block[GenerationConstants.CHUNK_SIZE][maximumY - minimumY][GenerationConstants.CHUNK_SIZE];
        this.biomes = new Biome[GenerationConstants.CHUNK_SIZE][maximumY - minimumY][GenerationConstants.CHUNK_SIZE];
        fill(Objects.requireNonNull(defaultBlock, "defaultBlock"));
        fillBiomes(Objects.requireNonNull(defaultBiome, "defaultBiome"));
    }

    public TestChunkGrid fill(Block block) {
        Objects.requireNonNull(block, "block");
        for (Block[][] xPlane : blocks) {
            for (Block[] yRow : xPlane) {
                Arrays.fill(yRow, block);
            }
        }
        return this;
    }

    public TestChunkGrid fill(int yMinimum, int yMaximum, Block block) {
        requireYRange(yMinimum, yMaximum);
        for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
            for (int y = yMinimum; y < yMaximum; y++) {
                for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                    setBlock(x, y, z, block);
                }
            }
        }
        return this;
    }

    public TestChunkGrid fillLayer(int y, Block block) {
        return fill(y, y + 1, block);
    }

    public TestChunkGrid fillColumn(int x, int z, int yMinimum, int yMaximum, Block block) {
        requireHorizontalPosition(x, z);
        requireYRange(yMinimum, yMaximum);
        for (int y = yMinimum; y < yMaximum; y++) {
            setBlock(x, y, z, block);
        }
        return this;
    }

    public TestChunkGrid fillBiomes(Biome biome) {
        Objects.requireNonNull(biome, "biome");
        for (Biome[][] xPlane : biomes) {
            for (Biome[] yRow : xPlane) {
                Arrays.fill(yRow, biome);
            }
        }
        return this;
    }

    public TestChunkGrid fillBiomeLayer(int y, Biome biome) {
        requireY(y);
        for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                setBiome(x, y, z, biome);
            }
        }
        return this;
    }

    public int airSectionsBottom() {
        for (int y = maximumY - 1; y >= minimumY; y--) {
            for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
                for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                    if (!getBlock(x, y, z).isAir()) {
                        return y + 1;
                    }
                }
            }
        }
        return minimumY;
    }

    public boolean containsY(int y) { return y >= minimumY && y < maximumY; }

    @Override
    public int getMaxHeight() { return maximumY; }

    @Override
    public int getMinHeight() { return minimumY; }

    @Override
    public int getChunkX() { return chunkX; }

    @Override
    public int getChunkZ() { return chunkZ; }

    @Override
    public Block getBlock(int x, int y, int z) {
        requirePosition(x, y, z);
        return blocks[x][y - minimumY][z];
    }

    @Override
    public Biome getBiome(int x, int y, int z) {
        requirePosition(x, y, z);
        return biomes[x][y - minimumY][z];
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Block block) {
        for (int x = xMin; x < xMax; x++) {
            for (int y = yMin; y < yMax; y++) {
                for (int z = zMin; z < zMax; z++) {
                    setBlock(x, y, z, block);
                }
            }
        }
    }

    @Override
    public void setBlock(int x, int y, int z, Block block) {
        requirePosition(x, y, z);
        blocks[x][y - minimumY][z] = Objects.requireNonNull(block, "block");
    }

    @Override
    public void setBiome(int x, int y, int z, Biome biome) {
        requirePosition(x, y, z);
        biomes[x][y - minimumY][z] = Objects.requireNonNull(biome, "biome");
    }

    private void requirePosition(int x, int y, int z) {
        requireHorizontalPosition(x, z);
        requireY(y);
    }

    private static void requireHorizontalPosition(int x, int z) {
        if (x < 0 || x >= GenerationConstants.CHUNK_SIZE || z < 0 || z >= GenerationConstants.CHUNK_SIZE) {
            throw new IndexOutOfBoundsException("Position outside chunk: " + x + ", " + z);
        }
    }

    private void requireY(int y) {
        if (!containsY(y)) {
            throw new IndexOutOfBoundsException("Y outside grid: " + y);
        }
    }

    private void requireYRange(int yMinimum, int yMaximum) {
        if (yMinimum < minimumY || yMaximum > maximumY || yMaximum < yMinimum) {
            throw new IndexOutOfBoundsException("Y range outside grid: [" + yMinimum + ", " + yMaximum + ")");
        }
    }
}
