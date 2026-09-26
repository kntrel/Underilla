package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;

/** A mutable vertical slice addressed by local horizontal and Y coordinates. */
public final class WorldSlice {

    private final Block[][] blocks;
    private final Biome[][] biomes;
    private final int offsetX;
    private final int offsetY;
    private boolean frozen;

    public WorldSlice(int width, int height) {
        this(0, 0, width, height);
    }

    /**
     * Creates a locally addressed slice with world-origin metadata used when composing tiles.
     */
    public WorldSlice(int offsetX, int offsetY, int width, int height) {
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

    /** Copies a complete region from a readable world into local slice coordinates. */
    public static WorldSlice from(WorldReader world, InspectionRegion region) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(region, "region");
        int startHorizontal = Math.multiplyExact(region.startChunk(), GenerationConstants.CHUNK_SIZE);
        int width = Math.multiplyExact(region.chunkLength(), GenerationConstants.CHUNK_SIZE);
        int height = Math.subtractExact(region.maximumY(), region.minimumY());
        WorldSlice slice = new WorldSlice(startHorizontal, region.minimumY(), width, height);

        for (int localHorizontal = 0; localHorizontal < width; localHorizontal++) {
            int worldHorizontal = startHorizontal + localHorizontal;
            for (int localY = 0; localY < height; localY++) {
                int worldY = region.minimumY() + localY;
                int worldX = region.axis() == InspectionRegion.Axis.Z
                        ? worldHorizontal
                        : region.coordinate();
                int worldZ = region.axis() == InspectionRegion.Axis.Z
                        ? region.coordinate()
                        : worldHorizontal;
                Block block = world.blockAt(worldX, worldY, worldZ).orElse(null);
                if (block != null) {
                    slice.setBlock(localHorizontal, localY, block);
                }
            }
        }
        copyBiomes(world, region, slice, startHorizontal);
        return slice;
    }

    /**
     * Detaches the tile contributed by one platform-owned chunk into local slice coordinates.
     */
    public static WorldSlice from(ChunkData chunk, InspectionRegion region) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(region, "region");
        if (!region.contains(chunk.getChunkX(), chunk.getChunkZ())) {
            throw new IllegalArgumentException("The target chunk is outside the inspection region");
        }
        if (region.minimumY() < chunk.getMinHeight() || region.maximumY() > chunk.getMaxHeight()) {
            throw new IllegalArgumentException("Slice Y range is outside the target chunk");
        }

        int chunkSize = GenerationConstants.CHUNK_SIZE;
        int traversedChunk = region.axis() == InspectionRegion.Axis.Z
                ? chunk.getChunkX()
                : chunk.getChunkZ();
        int startHorizontal = Math.multiplyExact(traversedChunk, chunkSize);
        int height = Math.subtractExact(region.maximumY(), region.minimumY());
        int fixedLocalCoordinate = Math.floorMod(region.coordinate(), chunkSize);
        WorldSlice slice = new WorldSlice(startHorizontal, region.minimumY(), chunkSize, height);

        for (int localHorizontal = 0; localHorizontal < chunkSize; localHorizontal++) {
            int localX = region.axis() == InspectionRegion.Axis.Z
                    ? localHorizontal
                    : fixedLocalCoordinate;
            int localZ = region.axis() == InspectionRegion.Axis.Z
                    ? fixedLocalCoordinate
                    : localHorizontal;
            for (int localY = 0; localY < height; localY++) {
                int worldY = region.minimumY() + localY;
                slice.setBlock(localHorizontal, localY, chunk.getBlock(localX, worldY, localZ));
            }
        }
        copyBiomes(chunk, region, slice, fixedLocalCoordinate);
        return slice;
    }

    private static void copyBiomes(
            WorldReader world,
            InspectionRegion region,
            WorldSlice slice,
            int startHorizontal
    ) {
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        int firstHorizontalCell = Math.floorDiv(startHorizontal, cellSize);
        int firstYCell = Math.floorDiv(region.minimumY(), cellSize);
        for (int cellX = 0; cellX < slice.getBiomeWidth(); cellX++) {
            int worldHorizontal = Math.max(startHorizontal, (firstHorizontalCell + cellX) * cellSize);
            int localHorizontal = worldHorizontal - startHorizontal;
            for (int cellY = 0; cellY < slice.getBiomeHeight(); cellY++) {
                int worldY = Math.max(region.minimumY(), (firstYCell + cellY) * cellSize);
                int localY = worldY - region.minimumY();
                int worldX = region.axis() == InspectionRegion.Axis.Z
                        ? worldHorizontal
                        : region.coordinate();
                int worldZ = region.axis() == InspectionRegion.Axis.Z
                        ? region.coordinate()
                        : worldHorizontal;
                Biome biome = world.biomeAt(worldX, worldY, worldZ).orElse(null);
                if (biome != null) {
                    slice.setBiome(cellX, cellY, biome);
                }
            }
        }
    }

    private static void copyBiomes(
            ChunkData chunk,
            InspectionRegion region,
            WorldSlice slice,
            int fixedLocalCoordinate
    ) {
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        int firstHorizontalCell = Math.floorDiv(slice.offsetX, cellSize);
        int firstYCell = Math.floorDiv(region.minimumY(), cellSize);
        for (int cellX = 0; cellX < slice.getBiomeWidth(); cellX++) {
            int worldHorizontal = Math.max(slice.offsetX, (firstHorizontalCell + cellX) * cellSize);
            int localHorizontal = worldHorizontal - slice.offsetX;
            int localX = region.axis() == InspectionRegion.Axis.Z
                    ? localHorizontal
                    : fixedLocalCoordinate;
            int localZ = region.axis() == InspectionRegion.Axis.Z
                    ? fixedLocalCoordinate
                    : localHorizontal;
            for (int cellY = 0; cellY < slice.getBiomeHeight(); cellY++) {
                int worldY = Math.max(region.minimumY(), (firstYCell + cellY) * cellSize);
                int localY = worldY - region.minimumY();
                slice.setBiome(cellX, cellY, chunk.getBiome(localX, worldY, localZ));
            }
        }
    }

    /** Returns a detached copy suitable for hand-off to another thread. */
    public WorldSlice copy() {
        WorldSlice copy = new WorldSlice(offsetX, offsetY, getWidth(), getHeight());
        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                Block block = getBlock(x, y);
                if (block != null) {
                    copy.setBlock(x, y, block);
                }
                Biome biome = getBiomeAt(x, y);
                if (biome != null) {
                    copy.setBiomeAt(x, y, biome);
                }
            }
        }
        return copy;
    }

    /** Prevents further mutation and returns this detached slice. */
    public WorldSlice freeze() {
        frozen = true;
        return this;
    }

    /** Copies every populated cell into this slice according to the slices' world origins. */
    public void copyFrom(WorldSlice source) {
        Objects.requireNonNull(source, "source");
        requireMutable();
        int destinationStartX = Math.subtractExact(source.offsetX, offsetX);
        int destinationStartY = Math.subtractExact(source.offsetY, offsetY);
        for (int x = 0; x < source.getWidth(); x++) {
            for (int y = 0; y < source.getHeight(); y++) {
                int destinationX = destinationStartX + x;
                int destinationY = destinationStartY + y;
                Block block = source.getBlock(x, y);
                if (block != null) {
                    setBlock(destinationX, destinationY, block);
                }
                Biome biome = source.getBiomeAt(x, y);
                if (biome != null) {
                    setBiomeAt(destinationX, destinationY, biome);
                }
            }
        }
    }

    public int getWidth() { return blocks.length; }

    public int getHeight() { return blocks[0].length; }

    public int getBiomeWidth() { return biomes.length; }

    public int getBiomeHeight() { return biomes[0].length; }

    /** World horizontal coordinate represented by local X zero. */
    public int getOffsetX() { return offsetX; }

    /** World Y coordinate represented by local Y zero. */
    public int getOffsetY() { return offsetY; }

    public Block getBlock(int x, int y) { return blocks[requireLocalX(x)][requireLocalY(y)]; }

    public void setBlock(int x, int y, Block block) {
        requireMutable();
        blocks[requireLocalX(x)][requireLocalY(y)] = Objects.requireNonNull(block, "block");
    }

    /** Returns the biome stored at local biome-cell coordinates. */
    public Biome getBiome(int x, int y) {
        return biomes[requireBiomeX(x)][requireBiomeY(y)];
    }

    /** Resolves local block coordinates to their corresponding local biome cell. */
    public Biome getBiomeAt(int x, int y) {
        requireLocalX(x);
        requireLocalY(y);
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        return getBiome(
                Math.floorDiv(offsetX + x, cellSize) - Math.floorDiv(offsetX, cellSize),
                Math.floorDiv(offsetY + y, cellSize) - Math.floorDiv(offsetY, cellSize));
    }

    /** Stores a biome at local biome-cell coordinates. */
    public void setBiome(int x, int y, Biome biome) {
        requireMutable();
        biomes[requireBiomeX(x)][requireBiomeY(y)] = Objects.requireNonNull(biome, "biome");
    }

    /** Resolves local block coordinates to their corresponding local biome cell before storing it. */
    public void setBiomeAt(int x, int y, Biome biome) {
        requireLocalX(x);
        requireLocalY(y);
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        setBiome(
                Math.floorDiv(offsetX + x, cellSize) - Math.floorDiv(offsetX, cellSize),
                Math.floorDiv(offsetY + y, cellSize) - Math.floorDiv(offsetY, cellSize),
                biome);
    }

    private int requireLocalX(int x) {
        if (x < 0 || x >= getWidth()) {
            throw new IndexOutOfBoundsException("X outside slice: " + x);
        }
        return x;
    }

    private int requireLocalY(int y) {
        if (y < 0 || y >= getHeight()) {
            throw new IndexOutOfBoundsException("Y outside slice: " + y);
        }
        return y;
    }

    private int requireBiomeX(int x) {
        if (x < 0 || x >= getBiomeWidth()) {
            throw new IndexOutOfBoundsException("Biome X outside slice: " + x);
        }
        return x;
    }

    private int requireBiomeY(int y) {
        if (y < 0 || y >= getBiomeHeight()) {
            throw new IndexOutOfBoundsException("Biome Y outside slice: " + y);
        }
        return y;
    }

    private void requireMutable() {
        if (frozen) {
            throw new IllegalStateException("The world slice is frozen");
        }
    }
}
