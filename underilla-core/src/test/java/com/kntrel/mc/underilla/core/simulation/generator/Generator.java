package com.kntrel.mc.underilla.core.simulation.generator;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.simulation.caver.BezierCurve;
import com.kntrel.mc.underilla.core.simulation.caver.CaveCurve;
import com.kntrel.mc.underilla.core.simulation.caver.CurveWalker;
import com.kntrel.mc.underilla.core.simulation.caver.Point;
import com.kntrel.mc.underilla.core.simulation.generator.noise.FastNoiseLite;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/** Hooks for the inspector's synthetic generation stages. */
public abstract class Generator {

    private static final int DEFAULT_CHUNKS_PER_AXIS = 8;
    private static final int CHUNK_SIZE = GenerationConstants.CHUNK_SIZE;
    private static final int CAVES_PER_CHUNK = 2;
    private static final double CAVE_DEVIATION_FACTOR = 16.0;
    private static final double CAVE_RESOLUTION_FACTOR = 0.1;
    private static final int CAVE_STEP_SIZE = 2;
    private static final int CAVE_CARVING_RADIUS = 2;
    private static final int TREE_ATTEMPT_LIMIT = 32;
    private static final Map<ID, Integer> TREE_QUOTAS = Map.ofEntries(
            Map.entry(ID.of("minecraft:plains"), 10),
            Map.entry(ID.of("minecraft:forest"), 4),
            Map.entry(ID.of("minecraft:flower_forest"), 4),
            Map.entry(ID.of("minecraft:birch_forest"), 4),
            Map.entry(ID.of("minecraft:dark_forest"), 6),
            Map.entry(ID.of("minecraft:taiga"), 4),
            Map.entry(ID.of("minecraft:old_growth_pine_taiga"), 5),
            Map.entry(ID.of("minecraft:old_growth_spruce_taiga"), 5),
            Map.entry(ID.of("minecraft:jungle"), 6),
            Map.entry(ID.of("minecraft:sparse_jungle"), 3),
            Map.entry(ID.of("minecraft:savanna"), 2),
            Map.entry(ID.of("minecraft:swamp"), 2));

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock STONE = TestBlock.solid("minecraft:stone");
    private static final TestBlock WATER = TestBlock.liquid("minecraft:water");
    private static final TestBlock GRASS = TestBlock.solid("minecraft:grass_block");
    private static final TestBlock DIRT = TestBlock.solid("minecraft:dirt");
    private static final TestBlock SAND = TestBlock.solid("minecraft:sand");

    protected final TestWorld world;
    protected final long seed;
    private final int chunksX;
    private final int chunksZ;
    private final FastNoiseLite terrainNoise;
    private final FastNoiseLite caveNoise;
    private Map<Chunk, List<CaveCurve>> plannedCaves = Map.of();

    public Generator(TestWorld world, long seed) {
        this(world, seed, DEFAULT_CHUNKS_PER_AXIS, DEFAULT_CHUNKS_PER_AXIS);
    }

    public Generator(TestWorld world, long seed, int chunksX, int chunksZ) {
        if (chunksX < 1 || chunksZ < 1) {
            throw new IllegalArgumentException("Chunk dimensions must be positive");
        }
        this.world = Objects.requireNonNull(world, "world");
        this.seed = seed;
        this.chunksX = chunksX;
        this.chunksZ = chunksZ;
        terrainNoise = new FastNoiseLite(Long.hashCode(seed));
        terrainNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        terrainNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        terrainNoise.SetFractalOctaves(4);
        terrainNoise.SetFrequency(0.008f);

        caveNoise = new FastNoiseLite(Long.hashCode(seed ^ 0x9E3779B97F4A7C15L));
        caveNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        caveNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        caveNoise.SetFractalOctaves(3);
        caveNoise.SetFrequency(0.035f);
    }

    /** Generates noise for the configured chunk area, then invokes its after-noise hooks. */
    public final void generate() {
        WorldInfo worldInfo = new WorldInfo(seed, world.getMinHeight(), world.getMaxHeight());
        List<Chunk> chunks = new ArrayList<>(Math.multiplyExact(chunksX, chunksZ));
        for (int chunkZ = 0; chunkZ < chunksZ; chunkZ++) {
            for (int chunkX = 0; chunkX < chunksX; chunkX++) {
                Chunk chunk = new Chunk(chunkX, chunkZ);
                chunks.add(chunk);
            }
        }
        plannedCaves = planCaves(worldInfo, chunks);
        for (Chunk chunk : chunks) {
            if (generateNoise(chunk)) {
                doNoise(worldInfo, chunk);
                afterNoise(worldInfo, chunk);
            }
            if (generateSurface(chunk)) {
                doSurface(worldInfo, chunk);
                afterSurface(worldInfo, chunk);
            }
            if (generateCaves(chunk)) {
                doCavers(worldInfo, chunk);
                afterCaves(worldInfo, chunk);
            }
            if (generateFeatures(chunk)) {
                doFeatures(chunk);
                afterFeatures(worldInfo, chunk);
            }
            afterLoad(worldInfo, chunk);
        }
    }

    private void doFeatures(Chunk chunk) {
        ChunkData chunkData = world.chunkData(chunk.x(), chunk.z()).orElse(null);
        if (chunkData == null) {
            return;
        }

        int topY = chunkData.getMaxHeight() - 1;
        int quota = 10;
        int placements = 0;
        for (int attempt = 0; attempt < TREE_ATTEMPT_LIMIT && placements < quota; attempt++) {
            SplittableRandom random = new SplittableRandom(featureSalt(chunk, attempt));
            int x = random.nextInt(CHUNK_SIZE);
            int z = random.nextInt(CHUNK_SIZE);
            if (Tree.place(chunkData, x, z)) {
                placements++;
            }
        }
    }

    private long featureSalt(Chunk chunk, int attempt) {
        long value = seed;
        value ^= 0x9E3779B97F4A7C15L * (chunk.x() + 1L);
        value ^= 0xBF58476D1CE4E5B9L * (chunk.z() + 1L);
        value ^= 0x94D049BB133111EBL * (attempt + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private Map<Chunk, List<CaveCurve>> planCaves(WorldInfo worldInfo, List<Chunk> chunks) {
        CaveGenerator caveGenerator = new CaveGenerator(
                new Point(0.0, worldInfo.minimumY(), 0.0),
                new Point(chunksX * (double) CHUNK_SIZE, worldInfo.maximumY(), chunksZ * (double) CHUNK_SIZE),
                seed,
                CAVE_DEVIATION_FACTOR,
                CAVE_RESOLUTION_FACTOR);

        Map<Chunk, List<CaveCurve>> cavesByChunk = new LinkedHashMap<>();
        int caveCount = Math.multiplyExact(chunks.size(), CAVES_PER_CHUNK);
        for (int caveIndex = 0; caveIndex < caveCount; caveIndex++) {
            CaveCurve cave = caveGenerator.generate(caveSalt(caveIndex));
            Bounds bounds = Bounds.of(cave).expanded(CAVE_CARVING_RADIUS);
            for (Chunk chunk : chunks) {
                if (bounds.intersects(chunk)) {
                    cavesByChunk.computeIfAbsent(chunk, _ -> new ArrayList<>()).add(cave);
                }
            }
        }
        return cavesByChunk;
    }

    private long caveSalt(int caveIndex) {
        long value = seed + 0x9E3779B97F4A7C15L * (caveIndex + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private void doCavers(WorldInfo worldInfo, Chunk chunk) {
        for (CaveCurve cave : plannedCaves.getOrDefault(chunk, List.of())) {
            for (Point point : new CurveWalker(cave, CAVE_STEP_SIZE)) {
                carveCube(worldInfo, chunk, point);
            }
        }
    }

    private void carveCube(WorldInfo worldInfo, Chunk chunk, Point center) {
        int chunkMinimumX = Math.multiplyExact(chunk.x(), CHUNK_SIZE);
        int chunkMaximumX = Math.addExact(chunkMinimumX, CHUNK_SIZE - 1);
        int chunkMinimumZ = Math.multiplyExact(chunk.z(), CHUNK_SIZE);
        int chunkMaximumZ = Math.addExact(chunkMinimumZ, CHUNK_SIZE - 1);
        int centerX = (int) Math.floor(center.x());
        int centerY = (int) Math.floor(center.y());
        int centerZ = (int) Math.floor(center.z());

        int minimumX = Math.max(chunkMinimumX, centerX - CAVE_CARVING_RADIUS);
        int maximumX = Math.min(chunkMaximumX, centerX + CAVE_CARVING_RADIUS);
        int minimumY = Math.max(worldInfo.minimumY(), centerY - CAVE_CARVING_RADIUS);
        int maximumY = Math.min(worldInfo.maximumY() - 1, centerY + CAVE_CARVING_RADIUS);
        int minimumZ = Math.max(chunkMinimumZ, centerZ - CAVE_CARVING_RADIUS);
        int maximumZ = Math.min(chunkMaximumZ, centerZ + CAVE_CARVING_RADIUS);
        for (int x = minimumX; x <= maximumX; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    world.setBlock(x, y, z, AIR);
                }
            }
        }
    }

    private void doNoise(WorldInfo worldInfo, Chunk chunk) {
        int minimumY = worldInfo.minimumY();
        int maximumY = worldInfo.maximumY();
        int seaLevel = Math.min(Math.max(63, minimumY), maximumY - 1);
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int x = chunk.x() * CHUNK_SIZE + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int z = chunk.z() * CHUNK_SIZE + localZ;
                double height = seaLevel + 8 + 42 * terrainNoise.GetNoise(x, z);
                for (int y = minimumY; y < maximumY; y++) {
                    boolean terrain = y < height;
                    boolean cave = terrain
                            && y > minimumY + 4
                            && y < height - 6
                            && caveNoise.GetNoise(x, y, z) > 0.18;
                    world.setBlock(x, y, z, terrain && !cave ? STONE : y <= seaLevel ? WATER : AIR);
                }
            }
        }
    }

    private void doSurface(WorldInfo worldInfo, Chunk chunk) {
        int minimumY = worldInfo.minimumY();
        int maximumY = worldInfo.maximumY();
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int x = chunk.x() * CHUNK_SIZE + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int z = chunk.z() * CHUNK_SIZE + localZ;
                for (int y = maximumY - 1; y >= minimumY; y--) {
                    Block block = world.blockAt(x, y, z).orElse(AIR);
                    if (!block.isSolid()) {
                        continue;
                    }

                    Block above = y == maximumY - 1
                            ? AIR
                            : world.blockAt(x, y + 1, z).orElse(AIR);
                    if (above.isAir()) {
                        world.setBlock(x, y, z, GRASS);
                        fillDirt(x, y - 1, z, minimumY);
                    } else if (above.isLiquid()) {
                        world.setBlock(x, y, z, SAND);
                    }
                    break;
                }
            }
        }
    }

    private void fillDirt(int x, int startY, int z, int minimumY) {
        int depth = 2 + (int) Math.floor((terrainNoise.GetNoise(x, z) + 1.0f) * 2.0f);
        for (int y = startY; y >= minimumY && y > startY - depth; y--) {
            Block block = world.blockAt(x, y, z).orElse(AIR);
            if (!block.isSolid()) {
                return;
            }
            world.setBlock(x, y, z, DIRT);
        }
    }

    /** Conservative three-dimensional bounds for a cave curve's Bézier control points. */
    private record Bounds(
            double minimumX,
            double minimumY,
            double minimumZ,
            double maximumX,
            double maximumY,
            double maximumZ) {

        private static Bounds of(CaveCurve cave) {
            BezierCurve first = cave.segments().get(0);
            Bounds bounds = new Bounds(
                    first.start().x(), first.start().y(), first.start().z(),
                    first.start().x(), first.start().y(), first.start().z());
            for (BezierCurve segment : cave.segments()) {
                bounds = bounds.include(segment.start());
                bounds = bounds.include(segment.firstControl());
                bounds = bounds.include(segment.secondControl());
                bounds = bounds.include(segment.end());
            }
            return bounds;
        }

        private Bounds include(Point point) {
            return new Bounds(
                    Math.min(minimumX, point.x()),
                    Math.min(minimumY, point.y()),
                    Math.min(minimumZ, point.z()),
                    Math.max(maximumX, point.x()),
                    Math.max(maximumY, point.y()),
                    Math.max(maximumZ, point.z()));
        }

        private Bounds expanded(double amount) {
            return new Bounds(
                    minimumX - amount,
                    minimumY - amount,
                    minimumZ - amount,
                    maximumX + amount,
                    maximumY + amount,
                    maximumZ + amount);
        }

        private boolean intersects(Chunk chunk) {
            int chunkMinimumX = Math.multiplyExact(chunk.x(), CHUNK_SIZE);
            int chunkMaximumX = Math.addExact(chunkMinimumX, CHUNK_SIZE);
            int chunkMinimumZ = Math.multiplyExact(chunk.z(), CHUNK_SIZE);
            int chunkMaximumZ = Math.addExact(chunkMinimumZ, CHUNK_SIZE);
            return minimumX < chunkMaximumX && maximumX >= chunkMinimumX
                    && minimumZ < chunkMaximumZ && maximumZ >= chunkMinimumZ;
        }
    }

    /** Returns whether synthetic noise should be generated for this chunk. */
    public abstract boolean generateNoise(Chunk chunk);

    public abstract void afterNoise(WorldInfo worldInfo, Chunk chunk);

    public abstract boolean generateSurface(Chunk chunk);

    public abstract void afterSurface(WorldInfo worldInfo, Chunk chunk);

    public abstract boolean generateCaves(Chunk chunk);

    public abstract void afterCaves(WorldInfo worldInfo, Chunk chunk);

    public abstract boolean generateFeatures(Chunk chunk);

    public abstract void afterFeatures(WorldInfo worldInfo, Chunk chunk);

    public abstract void afterLoad(WorldInfo worldInfo, Chunk chunk);
}
