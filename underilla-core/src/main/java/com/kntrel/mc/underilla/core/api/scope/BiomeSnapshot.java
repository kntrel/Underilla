package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.GenerationConstants;

/** Read-only biome identity and global 4x4x4 cell coordinates at evaluation time. */
public record BiomeSnapshot(ID id, int cellX, int cellY, int cellZ) implements Biome {

    /** Creates a type-only biome value at the cell origin. */
    public BiomeSnapshot(ID id) {
        this(id, 0, 0, 0);
    }

    /** Retains an existing snapshot's position; other biome values start at the cell origin. */
    public static BiomeSnapshot of(Biome biome) {
        if (biome == null) { return null; }
        return biome instanceof BiomeSnapshot snapshot ? snapshot : new BiomeSnapshot(biome.id());
    }

    /** Captures a sample at explicitly supplied global biome-cell coordinates. */
    public static BiomeSnapshot of(Biome biome, int cellX, int cellY, int cellZ) {
        return biome == null ? null : new BiomeSnapshot(biome.id(), cellX, cellY, cellZ);
    }

    /** Captures the biome cell containing the supplied global block position. */
    public static BiomeSnapshot atBlock(Biome biome, int x, int y, int z) {
        int size = GenerationConstants.BIOME_CELL_SIZE;
        return of(biome, Math.floorDiv(x, size), Math.floorDiv(y, size), Math.floorDiv(z, size));
    }
}
