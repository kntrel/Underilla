package com.kntrel.mc.underilla.core.api;

/** Mutable biome value associated with one global block position. */
public interface BiomeData {

    Biome get();

    void set(Biome biome);

    int getX();

    int getY();

    int getZ();

    default int getChunkX() { return Math.floorDiv(getX(), GenerationConstants.CHUNK_SIZE); }

    default int getChunkZ() { return Math.floorDiv(getZ(), GenerationConstants.CHUNK_SIZE); }

    /** Returns the X coordinate of the 4x4x4 biome cell containing this position. */
    default int getBiomeX() { return Math.floorDiv(getX(), GenerationConstants.BIOME_CELL_SIZE); }

    /** Returns the Y coordinate of the 4x4x4 biome cell containing this position. */
    default int getBiomeY() { return Math.floorDiv(getY(), GenerationConstants.BIOME_CELL_SIZE); }

    /** Returns the Z coordinate of the 4x4x4 biome cell containing this position. */
    default int getBiomeZ() { return Math.floorDiv(getZ(), GenerationConstants.BIOME_CELL_SIZE); }
}
