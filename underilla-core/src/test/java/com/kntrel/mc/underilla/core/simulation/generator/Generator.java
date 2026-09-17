package com.kntrel.mc.underilla.core.simulation.generator;

import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.simulation.generator.noise.FastNoiseLite;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Hooks for the inspector's synthetic generation stages. */
public abstract class Generator {

    private static final int DEFAULT_CHUNKS_PER_AXIS = 8;
    private static final int CHUNK_SIZE = GenerationConstants.CHUNK_SIZE;
    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock STONE = TestBlock.solid("minecraft:stone");
    private static final TestBlock WATER = TestBlock.liquid("minecraft:water");

    protected final TestWorld world;
    protected final long seed;
    private final int chunksX;
    private final int chunksZ;
    private final FastNoiseLite terrainNoise;
    private final FastNoiseLite caveNoise;

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
                if (generateNoise(chunk)) {
                    doNoise(worldInfo, chunk);
                }
            }
        }
        for (Chunk chunk : chunks) {
            afterNoise(worldInfo, chunk);
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
