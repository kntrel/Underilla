package com.kntrel.mc.underilla.core.reader;

import com.jkantrell.mca.Chunk;
import com.jkantrell.mca.MCAFile;
import com.jkantrell.mca.MCAUtil;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.cache.TopicChunkCache;
import java.io.File;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Objects;
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
    private final File entitites;
    private final RLUCache<RegionBucket> regionCache;
    private final TopicChunkCache<ChunkReaders> chunkReaders;
    private final DiskWorldReader source;
    private final RLUCacheTriple<Biome> biomeCache;

    protected DiskWorldReader(String regionPath, int cacheSize) throws NoSuchFieldException {
        this(new File(regionPath), null, cacheSize);
    }

    protected DiskWorldReader(String regionPath, String entityRegionPath, int cacheSize) throws NoSuchFieldException {
        this(new File(regionPath), entityRegionPath == null ? null : new File(entityRegionPath), cacheSize);
    }

    protected DiskWorldReader(File regionDirectory, int cacheSize) throws NoSuchFieldException {
        this(regionDirectory, null, cacheSize);
    }

    protected DiskWorldReader(File regionDirectory, File entityDirectory, int cacheSize) throws NoSuchFieldException {
        if (!(regionDirectory.exists() && regionDirectory.isDirectory())) {
            throw new NoSuchFieldException("Region directory '" + regionDirectory.getPath() + "' does not exist.");
        }
        if (entityDirectory != null && !(entityDirectory.exists() && entityDirectory.isDirectory())) {
            throw new NoSuchFieldException("Entity region directory '" + entityDirectory.getPath() + "' does not exist.");
        }
        this.regions = regionDirectory;
        this.entitites = entityDirectory;
        this.regionCache = new RLUCache<>(cacheSize);
        int chunkCacheSize = cacheSize * 64;
        this.chunkReaders = null;
        this.source = this;
        this.biomeCache = new RLUCacheTriple<>(chunkCacheSize * GenerationConstants.BIOME_CELL_SIZE
                * GenerationConstants.BIOME_CELL_SIZE);
    }

    private DiskWorldReader(DiskWorldReader reader, ChunkCache cache) {
        this.regions = reader.regions;
        this.entitites = reader.entitites;
        this.regionCache = reader.regionCache;
        this.biomeCache = reader.biomeCache;
        this.source = reader.source;
        this.chunkReaders = Objects.requireNonNull(cache, "cache").topicView(ChunkReaders.class);
    }

    /**
     * Creates a plan-local view using the supplied cache for chunk readers. Region and biome
     * storage remain shared with this reader. The original reader is unchanged and reads chunks
     * without retaining their wrappers until a cache is supplied through this method.
     */
    public final DiskWorldReader withChunkCache(ChunkCache cache) {
        return new DiskWorldReader(this, cache) {
            @Override
            protected ChunkReader newChunkReader(Chunk chunk, List<EntityView> entities) {
                return DiskWorldReader.this.source.newChunkReader(chunk, entities);
            }
        };
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
        if (chunkReaders == null) {
            return readUncachedChunk(x, z);
        }
        ChunkReaders readers = chunkReaders.getOrCompute(x, z, ChunkReaders::new);
        synchronized (readers) {
            ChunkReader cached = readers.bySource.get(source);
            if (cached != null) {
                return Optional.of(cached);
            }
            Optional<ChunkReader> loaded = readUncachedChunk(x, z);
            loaded.ifPresent(value -> readers.bySource.put(source, value));
            return loaded;
        }
    }

    private Optional<ChunkReader> readUncachedChunk(int x, int z) {
        RegionBucket region = readRegion(x >> 5, z >> 5);
        if (region == null) {
            return Optional.empty();
        }
        int localX = Math.floorMod(x, 32);
        int localZ = Math.floorMod(z, 32);
        Chunk chunk = region.terrain().getChunk(localX, localZ);
        if (chunk == null) {
            return Optional.empty();
        }
        Chunk entityChunk = region.entities() == null ? null : region.entities().getChunk(localX, localZ);
        ChunkReader chunkReader = newChunkReader(chunk, entityViews(entityChunk));
        return Optional.of(chunkReader);
    }

    @Override
    public ID getBiomeID(int globalX, int globalY, int globalZ) {
        return biomeAt(globalX, globalY, globalZ).map(Biome::id).orElse(null);
    }

    protected abstract ChunkReader newChunkReader(Chunk chunk, List<EntityView> entities);

    private RegionBucket readRegion(int x, int z) {
        RegionBucket cachedRegion = regionCache.get(x, z);
        if (cachedRegion != null) {
            return cachedRegion;
        }
        MCAFile terrain = readRegionFile(regions, x, z);
        if (terrain == null) {
            return null;
        }
        MCAFile entities = entitites == null ? null : readRegionFile(entitites, x, z);
        RegionBucket region = new RegionBucket(terrain, entities);
        regionCache.put(x, z, region);
        return region;
    }

    private MCAFile readRegionFile(File directory, int x, int z) {
        File regionFile = new File(directory, "r." + x + "." + z + ".mca");
        if (!regionFile.exists()) {
            return null;
        }
        try {
            return MCAUtil.read(regionFile);
        } catch (Exception exception) {
            LOGGER.error("Failed to read region file '{}'", regionFile.getPath(), exception);
            return null;
        }
    }

    private static List<EntityView> entityViews(Chunk chunk) {
        if (chunk == null || chunk.getEntities() == null) {
            return List.of();
        }
        List<EntityView> entities = new ArrayList<>(chunk.getEntities().size());
        for (var entity : chunk.getEntities()) {
            entities.add(new EntityView(entity, chunk.getDataVersion()));
        }
        return List.copyOf(entities);
    }

    private static final class ChunkReaders {
        private final Map<DiskWorldReader, ChunkReader> bySource = new IdentityHashMap<>();
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

    private record RegionBucket(MCAFile terrain, MCAFile entities) {}
}
