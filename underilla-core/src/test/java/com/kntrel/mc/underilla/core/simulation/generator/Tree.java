package com.kntrel.mc.underilla.core.simulation.generator;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.impl.TestBlock;

/** A small synthetic oak tree used by the generation simulator. */
final class Tree {

    private static final ID GRASS_BLOCK = ID.of("minecraft:grass_block");
    private static final Block LOG = TestBlock.solid("minecraft:oak_log");
    private static final Block LEAVES = TestBlock.solid("minecraft:oak_leaves");

    private static final int RADIUS = 2;
    private static final int WIDTH = RADIUS * 2 + 1;
    private static final int HEIGHT = 6;

    /** Indexed by relative x + radius, relative y, and relative z + radius. */
    private static final Block[][][] GRID = createGrid();

    private Tree() {}

    static boolean place(ChunkData chunk, int x, int z) {
        for (int y = chunk.getMaxHeight() - 1; y >= chunk.getMinHeight(); y--) {
            Block ground = chunk.getBlock(x, y, z);
            if (ground.isAir()) {
                continue;
            }
            if (!GRASS_BLOCK.equals(ground.id())) {
                return false;
            }

            int lowestLogY = y + 1;
            if (!fits(chunk, lowestLogY)) {
                return false;
            }
            placeGrid(chunk, x, lowestLogY, z);
            return true;
        }
        return false;
    }

    private static boolean fits(ChunkData chunk, int y) {
        return y >= chunk.getMinHeight()
                && y + HEIGHT <= chunk.getMaxHeight();
    }

    private static void placeGrid(ChunkData chunk, int x, int y, int z) {
        for (int gridX = 0; gridX < WIDTH; gridX++) {
            for (int gridY = 0; gridY < HEIGHT; gridY++) {
                for (int gridZ = 0; gridZ < WIDTH; gridZ++) {
                    Block block = GRID[gridX][gridY][gridZ];
                    int blockX = x + gridX - RADIUS;
                    int blockZ = z + gridZ - RADIUS;
                    if (block != null && insideChunk(blockX, blockZ)) {
                        chunk.setBlock(blockX, y + gridY, blockZ, block);
                    }
                }
            }
        }
    }

    private static boolean insideChunk(int x, int z) {
        return x >= 0 && x < GenerationConstants.CHUNK_SIZE
                && z >= 0 && z < GenerationConstants.CHUNK_SIZE;
    }

    private static Block[][][] createGrid() {
        Block[][][] grid = new Block[WIDTH][HEIGHT][WIDTH];
        for (int y = 0; y < 5; y++) {
            grid[RADIUS][y][RADIUS] = LOG;
        }
        fillLeafLayer(grid, 2, 2);
        fillLeafLayer(grid, 3, 2);
        fillLeafLayer(grid, 4, 1);
        grid[RADIUS][5][RADIUS] = LEAVES;
        grid[RADIUS - 1][5][RADIUS] = LEAVES;
        grid[RADIUS + 1][5][RADIUS] = LEAVES;
        grid[RADIUS][5][RADIUS - 1] = LEAVES;
        grid[RADIUS][5][RADIUS + 1] = LEAVES;
        return grid;
    }

    private static void fillLeafLayer(Block[][][] grid, int y, int radius) {
        for (int x = RADIUS - radius; x <= RADIUS + radius; x++) {
            for (int z = RADIUS - radius; z <= RADIUS + radius; z++) {
                boolean corner = Math.abs(x - RADIUS) == radius && Math.abs(z - RADIUS) == radius;
                if (!corner && grid[x][y][z] == null) {
                    grid[x][y][z] = LEAVES;
                }
            }
        }
    }
}
