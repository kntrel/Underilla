package com.kntrel.mc.underilla.core.reader;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.vector.Vector;
import java.util.Optional;

/**
 * Read-only access to a world.
 *
 * <p>The source of the world data is deliberately unspecified. Production
 * implementations may read region files, while tests can provide an in-memory
 * implementation.</p>
 */
public interface WorldReader {

    Optional<Block> blockAt(int x, int y, int z);

    Optional<Biome> biomeAt(int x, int y, int z);

    default Optional<Block> blockAt(Vector<Integer> position) {
        return blockAt(position.x(), position.y(), position.z());
    }

    default Optional<Biome> biomeAt(Vector<Integer> position) {
        return biomeAt(position.x(), position.y(), position.z());
    }

    default Optional<Biome> biomeAtCell(int x, int y, int z) {
        return biomeAt(x << 2, y << 2, z << 2);
    }

    default Optional<Biome> biomeAtCell(Vector<Integer> position) {
        return biomeAtCell(position.x(), position.y(), position.z());
    }

    Optional<ChunkReader> readChunk(int chunkX, int chunkZ);

    default String getBiomeName(int globalX, int globalY, int globalZ) {
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        int cellX = Math.floorDiv(globalX, cellSize) * cellSize;
        int cellY = Math.floorDiv(globalY, cellSize) * cellSize;
        int cellZ = Math.floorDiv(globalZ, cellSize) * cellSize;
        Optional<Biome> biome = biomeAt(cellX, cellY, cellZ);
        return biome.map(Biome::getName).orElse(null);
    }
}
