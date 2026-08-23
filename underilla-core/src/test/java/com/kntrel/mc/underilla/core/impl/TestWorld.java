package com.kntrel.mc.underilla.core.impl;

import com.jkantrell.nbt.tag.CompoundTag;
import com.jkantrell.nbt.tag.StringTag;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.LocatedBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Coordinate-aware in-memory world composed of {@link TestChunkGrid} instances. */
public final class TestWorld implements WorldReader {

    private final Map<ChunkCoordinate, TestChunkGrid> chunks = new HashMap<>();

    public TestWorld addChunk(TestChunkGrid chunk) {
        Objects.requireNonNull(chunk, "chunk");
        chunks.put(new ChunkCoordinate(chunk.getChunkX(), chunk.getChunkZ()), chunk);
        return this;
    }

    @Override
    public Optional<Block> blockAt(int x, int y, int z) {
        return gridAt(x, z).filter(grid -> grid.containsY(y))
                .map(grid -> grid.getBlock(Math.floorMod(x, GenerationConstants.CHUNK_SIZE), y,
                        Math.floorMod(z, GenerationConstants.CHUNK_SIZE)));
    }

    @Override
    public Optional<Biome> biomeAt(int x, int y, int z) {
        return gridAt(x, z).filter(grid -> grid.containsY(y))
                .map(grid -> grid.getBiome(Math.floorMod(x, GenerationConstants.CHUNK_SIZE), y,
                        Math.floorMod(z, GenerationConstants.CHUNK_SIZE)));
    }

    @Override
    public Optional<ChunkReader> readChunk(int chunkX, int chunkZ) {
        return Optional.ofNullable(chunks.get(new ChunkCoordinate(chunkX, chunkZ))).map(GridChunkReader::new);
    }

    private Optional<TestChunkGrid> gridAt(int globalX, int globalZ) {
        int chunkX = Math.floorDiv(globalX, GenerationConstants.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(globalZ, GenerationConstants.CHUNK_SIZE);
        return Optional.ofNullable(chunks.get(new ChunkCoordinate(chunkX, chunkZ)));
    }

    private record ChunkCoordinate(int x, int z) {}

    private static final class GridChunkReader extends ChunkReader {

        private final TestChunkGrid grid;

        private GridChunkReader(TestChunkGrid grid) {
            super(null);
            this.grid = grid;
        }

        @Override
        public int getX() { return grid.getChunkX(); }

        @Override
        public int getZ() { return grid.getChunkZ(); }

        @Override
        public int getGlobalX(int localX) {
            return grid.getChunkX() * GenerationConstants.CHUNK_SIZE + localX;
        }

        @Override
        public int getGlobalZ(int localZ) {
            return grid.getChunkZ() * GenerationConstants.CHUNK_SIZE + localZ;
        }

        @Override
        public Optional<Block> blockAt(int x, int y, int z) {
            if (!grid.containsY(y) || x < 0 || x >= GenerationConstants.CHUNK_SIZE
                    || z < 0 || z >= GenerationConstants.CHUNK_SIZE) {
                return Optional.empty();
            }
            return Optional.of(grid.getBlock(x, y, z));
        }

        @Override
        public Optional<Biome> biomeAt(int x, int y, int z) {
            if (!grid.containsY(y) || x < 0 || x >= GenerationConstants.CHUNK_SIZE
                    || z < 0 || z >= GenerationConstants.CHUNK_SIZE) {
                return Optional.empty();
            }
            return Optional.of(grid.getBiome(x, y, z));
        }

        @Override
        public int airSectionsBottom() { return grid.airSectionsBottom(); }

        @Override
        public List<LocatedBlock> locationsOf(Predicate<Block> checker) {
            List<LocatedBlock> locations = new ArrayList<>();
            for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
                for (int y = grid.getMinHeight(); y < grid.getMaxHeight(); y++) {
                    for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                        Block block = grid.getBlock(x, y, z);
                        if (checker.test(block)) {
                            locations.add(new LocatedBlock(x, y, z, block));
                        }
                    }
                }
            }
            return locations;
        }

        @Override
        public Optional<Block> blockFromTag(CompoundTag tag) { return Optional.empty(); }

        @Override
        public Optional<Block> blockFromTag(CompoundTag tag, CompoundTag entityTag) { return Optional.empty(); }

        @Override
        public Optional<Biome> biomeFromTag(StringTag tag) { return Optional.empty(); }
    }
}
