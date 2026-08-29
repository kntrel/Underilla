package com.kntrel.mc.underilla.core.reader;

import com.jkantrell.mca.Chunk;
import com.jkantrell.mca.MCAFile;
import com.jkantrell.mca.MCAUtil;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import java.io.File;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads chunks directly from a directory of Anvil region files.
 *
 * <p>This reader deliberately has no knowledge of Minecraft world or dimension directory layouts. Chunk conversion
 * remains platform-specific, so subclasses provide the appropriate {@link ChunkReader} implementation.</p>
 */
public abstract class DiskWorldReader implements WorldReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiskWorldReader.class);
    private final File regions;
    private final RLUCache<MCAFile> regionCache;
    private final RLUCache<ChunkReader> chunkCache;
    private final RLUCacheTriple<Biome> biomeCache;

    protected DiskWorldReader(String regionPath, int cacheSize) throws NoSuchFieldException {
        this(new File(regionPath), cacheSize);
    }

    protected DiskWorldReader(File regionDirectory, int cacheSize) throws NoSuchFieldException {
        if (!(regionDirectory.exists() && regionDirectory.isDirectory())) {
            throw new NoSuchFieldException("Region directory '" + regionDirectory.getPath() + "' does not exist.");
        }
        this.regions = regionDirectory;
        this.regionCache = new RLUCache<>(cacheSize);
        int chunkCacheSize = cacheSize * 64;
        this.chunkCache = new RLUCache<>(chunkCacheSize);
        this.biomeCache = new RLUCacheTriple<>(chunkCacheSize * GenerationConstants.BIOME_CELL_SIZE
                * GenerationConstants.BIOME_CELL_SIZE);
    }

    @Override
    public Optional<Block> blockAt(int x, int y, int z) {
        int chunkX = MCAUtil.blockToChunk(x);
        int chunkZ = MCAUtil.blockToChunk(z);
        return readChunk(chunkX, chunkZ)
                .flatMap(chunk -> chunk.blockAt(Math.floorMod(x, 16), y, Math.floorMod(z, 16)));
    }

    @Override
    public Optional<Biome> biomeAt(int x, int y, int z) {
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        int cellX = Math.floorDiv(x, cellSize) * cellSize;
        int cellY = Math.floorDiv(y, cellSize) * cellSize;
        int cellZ = Math.floorDiv(z, cellSize) * cellSize;
        Biome cachedBiome = biomeCache.get(cellX, cellY, cellZ);
        if (cachedBiome != null) {
            return Optional.of(cachedBiome);
        }

        int chunkX = MCAUtil.blockToChunk(cellX);
        int chunkZ = MCAUtil.blockToChunk(cellZ);
        Optional<Biome> biome = readChunk(chunkX, chunkZ)
                .flatMap(chunk -> chunk.biomeAt(Math.floorMod(cellX, 16), cellY, Math.floorMod(cellZ, 16)));
        biome.ifPresent(value -> biomeCache.put(cellX, cellY, cellZ, value));
        return biome;
    }

    @Override
    public Optional<ChunkReader> readChunk(int x, int z) {
        ChunkReader cachedChunk = chunkCache.get(x, z);
        if (cachedChunk != null) {
            return Optional.of(cachedChunk);
        }
        MCAFile region = readRegion(x >> 5, z >> 5);
        if (region == null) {
            return Optional.empty();
        }
        Chunk chunk = region.getChunk(Math.floorMod(x, 32), Math.floorMod(z, 32));
        if (chunk == null) {
            return Optional.empty();
        }
        ChunkReader chunkReader = newChunkReader(chunk);
        chunkCache.put(x, z, chunkReader);
        return Optional.of(chunkReader);
    }

    @Override
    public String getBiomeName(int globalX, int globalY, int globalZ) {
        return biomeAt(globalX, globalY, globalZ).map(Biome::getName).orElse(null);
    }

    protected abstract ChunkReader newChunkReader(Chunk chunk);

    private MCAFile readRegion(int x, int z) {
        MCAFile cachedRegion = regionCache.get(x, z);
        if (cachedRegion != null) {
            return cachedRegion;
        }
        File regionFile = new File(regions, "r." + x + "." + z + ".mca");
        if (!regionFile.exists()) {
            return null;
        }
        try {
            MCAFile region = MCAUtil.read(regionFile);
            regionCache.put(x, z, region);
            return region;
        } catch (Exception exception) {
            LOGGER.error("Failed to read region file '{}'", regionFile.getPath(), exception);
            return null;
        }
    }

    private static final class RLUCache<T> {
        private final Map<ChunkCoordinate, T> map = new HashMap<>();
        private final Deque<ChunkCoordinate> queue = new LinkedList<>();
        private final int capacity;

        private RLUCache(int capacity) {
            this.capacity = capacity;
        }

        private synchronized T get(int x, int z) {
            return map.get(new ChunkCoordinate(x, z));
        }

        private synchronized void put(int x, int z, T value) {
            ChunkCoordinate coordinate = new ChunkCoordinate(x, z);
            if (map.containsKey(coordinate)) {
                queue.remove(coordinate);
            } else if (queue.size() >= capacity) {
                try {
                    map.remove(queue.removeLast());
                } catch (NoSuchElementException ignored) {
                    // A zero-sized cache has no entry to evict.
                }
            }
            map.put(coordinate, value);
            queue.addFirst(coordinate);
        }
    }

    private static final class RLUCacheTriple<T> {
        private final Map<BiomeCoordinate, T> map = new HashMap<>();
        private final Deque<BiomeCoordinate> queue = new LinkedList<>();
        private final int capacity;

        private RLUCacheTriple(int capacity) {
            this.capacity = capacity;
        }

        private synchronized T get(int x, int y, int z) {
            return map.get(new BiomeCoordinate(x, y, z));
        }

        private synchronized void put(int x, int y, int z, T value) {
            BiomeCoordinate coordinate = new BiomeCoordinate(x, y, z);
            if (map.containsKey(coordinate)) {
                queue.remove(coordinate);
            } else if (queue.size() >= capacity) {
                try {
                    map.remove(queue.removeLast());
                } catch (NoSuchElementException ignored) {
                    // A zero-sized cache has no entry to evict.
                }
            }
            map.put(coordinate, value);
            queue.addFirst(coordinate);
        }
    }

    private record ChunkCoordinate(int x, int z) {}

    private record BiomeCoordinate(int x, int y, int z) {}
}
