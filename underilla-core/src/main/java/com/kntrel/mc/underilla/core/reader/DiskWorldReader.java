package com.kntrel.mc.underilla.core.reader;

import com.jkantrell.mca.Chunk;
import com.jkantrell.mca.MCAFile;
import com.jkantrell.mca.MCAUtil;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.GenerationLogger;
import java.io.File;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads a Minecraft world from Anvil region files on disk.
 *
 * <p>Chunk conversion remains platform-specific, so subclasses provide the
 * appropriate {@link ChunkReader} implementation.</p>
 */
public abstract class DiskWorldReader implements WorldReader {

    private static final String REGION_DIRECTORY = "region";

    private final File world;
    private final File regions;
    private final RLUCache<MCAFile> regionCache;
    private final RLUCache<ChunkReader> chunkCache;
    private final RLUCacheTriple<String> biomeCache;
    private final GenerationLogger logger;

    protected DiskWorldReader(String worldPath, int cacheSize, GenerationLogger logger) throws NoSuchFieldException {
        this(new File(worldPath), cacheSize, logger);
    }

    protected DiskWorldReader(File worldDirectory, int cacheSize, GenerationLogger logger) throws NoSuchFieldException {
        if (!(worldDirectory.exists() && worldDirectory.isDirectory())) {
            throw new NoSuchFieldException("World directory '" + worldDirectory.getPath() + "' does not exist.");
        }
        File regionDirectory = new File(worldDirectory, REGION_DIRECTORY);
        if (!(regionDirectory.exists() && regionDirectory.isDirectory())) {
            throw new NoSuchFieldException("World '" + worldDirectory.getName() + "' doesn't have a 'region' directory.");
        }
        this.world = worldDirectory;
        this.regions = regionDirectory;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.regionCache = new RLUCache<>(cacheSize);
        int chunkCacheSize = cacheSize * 64;
        this.chunkCache = new RLUCache<>(chunkCacheSize);
        this.biomeCache = new RLUCacheTriple<>(chunkCacheSize * GenerationConstants.BIOME_CELL_SIZE
                * GenerationConstants.BIOME_CELL_SIZE);
    }

    public String getWorldName() {
        return world.getName();
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
        int chunkX = MCAUtil.blockToChunk(x);
        int chunkZ = MCAUtil.blockToChunk(z);
        return readChunk(chunkX, chunkZ)
                .flatMap(chunk -> chunk.biomeAt(Math.floorMod(x, 16), y, Math.floorMod(z, 16)));
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
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        globalX = globalX - globalX % cellSize;
        globalY = globalY - globalY % cellSize;
        globalZ = globalZ - globalZ % cellSize;

        String cachedBiome = biomeCache.get(globalX, globalY, globalZ);
        if (cachedBiome != null) {
            return cachedBiome;
        }

        String biomeName = biomeAt(globalX, globalY, globalZ).map(Biome::getName).orElse(null);
        biomeCache.put(globalX, globalY, globalZ, biomeName);
        return biomeName;
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
            logger.error("Failed to read region file '" + regionFile.getPath() + "'", exception);
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
