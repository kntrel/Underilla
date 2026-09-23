package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;

/** A mutable X-Y slice addressed by world block coordinates. */
public final class WorldSlice {

    private final Block[][] blocks;
    private final Biome[][] biomes;
    private final int offsetX;
    private final int offsetY;

    public WorldSlice(int width, int height) {
        this(0, 0, width, height);
    }

    private WorldSlice(int offsetX, int offsetY, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Slice width and height must be positive");
        }
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        blocks = new Block[width][height];
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        int biomeStartX = Math.floorDiv(offsetX, cellSize);
        int biomeStartY = Math.floorDiv(offsetY, cellSize);
        int biomeEndX = Math.floorDiv(offsetX + width - 1, cellSize);
        int biomeEndY = Math.floorDiv(offsetY + height - 1, cellSize);
        biomes = new Biome[biomeEndX - biomeStartX + 1][biomeEndY - biomeStartY + 1];
    }

    /** Copies the X-Y slice at {@code z}; end coordinates are exclusive. */
    public static WorldSlice from(WorldReader world, int z, int startX, int endX, int startY, int endY) {
        Objects.requireNonNull(world, "world");
        if (endX <= startX || endY <= startY) {
            throw new IllegalArgumentException("Slice ranges must be nonempty");
        }
        WorldSlice slice = new WorldSlice(
                startX,
                startY,
                Math.subtractExact(endX, startX),
                Math.subtractExact(endY, startY)
        );
        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                Block block = world.blockAt(x, y, z).orElse(null);
                if (block != null) {
                    slice.setBlock(x, y, block);
                }
            }
        }
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        int biomeOffsetX = Math.floorDiv(slice.offsetX, cellSize);
        int biomeOffsetY = Math.floorDiv(slice.offsetY, cellSize);
        for (int cellX = 0; cellX < slice.getBiomeWidth(); cellX++) {
            int x = Math.max(startX, (biomeOffsetX + cellX) * cellSize);
            for (int cellY = 0; cellY < slice.getBiomeHeight(); cellY++) {
                int y = Math.max(startY, (biomeOffsetY + cellY) * cellSize);
                world.biomeAt(x, y, z).ifPresent(biome -> slice.setBiome(x, y, biome));
            }
        }
        return slice;
    }

    public int getWidth() { return blocks.length; }

    public int getHeight() { return blocks[0].length; }

    public int getBiomeWidth() { return biomes.length; }

    public int getBiomeHeight() { return biomes[0].length; }

    public int getOffsetX() { return offsetX; }

    public int getOffsetY() { return offsetY; }

    public Block getBlock(int x, int y) { return blocks[localX(x)][localY(y)]; }

    public void setBlock(int x, int y, Block block) {
        blocks[localX(x)][localY(y)] = Objects.requireNonNull(block, "block");
    }

    /** Looks up the biome cell containing the given world block coordinates. */
    public Biome getBiome(int x, int y) {
        localX(x);
        localY(y);
        return biomes[Math.floorDiv(x, GenerationConstants.BIOME_CELL_SIZE)
                - Math.floorDiv(offsetX, GenerationConstants.BIOME_CELL_SIZE)]
                [Math.floorDiv(y, GenerationConstants.BIOME_CELL_SIZE)
                - Math.floorDiv(offsetY, GenerationConstants.BIOME_CELL_SIZE)];
    }

    public void setBiome(int x, int y, Biome biome) {
        localX(x);
        localY(y);
        biomes[Math.floorDiv(x, GenerationConstants.BIOME_CELL_SIZE)
                - Math.floorDiv(offsetX, GenerationConstants.BIOME_CELL_SIZE)]
                [Math.floorDiv(y, GenerationConstants.BIOME_CELL_SIZE)
                - Math.floorDiv(offsetY, GenerationConstants.BIOME_CELL_SIZE)]
                = Objects.requireNonNull(biome, "biome");
    }

    private int localX(int x) {
        long local = (long) x - offsetX;
        if (local < 0 || local >= getWidth()) {
            throw new IndexOutOfBoundsException("X outside slice: " + x);
        }
        return (int) local;
    }

    private int localY(int y) {
        long local = (long) y - offsetY;
        if (local < 0 || local >= getHeight()) {
            throw new IndexOutOfBoundsException("Y outside slice: " + y);
        }
        return (int) local;
    }
}
