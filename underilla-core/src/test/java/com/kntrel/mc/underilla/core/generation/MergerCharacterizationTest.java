package com.kntrel.mc.underilla.core.generation;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestDiskWorldReader;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class MergerCharacterizationTest {

    private static final int MINIMUM_Y = -64;
    private static final int MAXIMUM_Y = 320;
    private static final int ABSOLUTE_MERGE_BOUNDARY_Y = 64;
    private static final int SURFACE_MERGE_DEPTH = 6;
    private static final int ADAPTIVE_MAXIMUM_DEPTH = 50;
    private static final int ADAPTIVE_MINIMUM_HIDDEN_DEPTH = 2;
    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock VOID = TestBlock.nonSolid("__void__");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");
    private static final Set<String> IGNORED_SURFACE_BLOCKS = Set.of(
            "minecraft:birch_leaves",
            "minecraft:birch_log",
            "minecraft:ice",
            "minecraft:oak_leaves",
            "minecraft:oak_log",
            "minecraft:spruce_leaves",
            "minecraft:spruce_log");
    private static final List<ChunkCoordinate> REPRESENTATIVE_CHUNKS = List.of(
            new ChunkCoordinate(0, 0),
            new ChunkCoordinate(7, 1),
            new ChunkCoordinate(18, 14),
            new ChunkCoordinate(19, 16),
            new ChunkCoordinate(20, 16),
            new ChunkCoordinate(21, 16),
            new ChunkCoordinate(21, 18),
            new ChunkCoordinate(20, 25),
            new ChunkCoordinate(16, 14),
            new ChunkCoordinate(17, 14),
            new ChunkCoordinate(23, 0),
            new ChunkCoordinate(30, 30));
    @TempDir
    Path temporaryDirectory;

    private TestBlockFactory blocks;
    private TestDiskWorldReader surfaceWorld;
    private TestDiskWorldReader cavesWorld;
    private TestGenerationConfig surfaceConfig;
    private LegacyMergerPatcher absolutePatcher;
    private LegacyMergerPatcher surfacePatcher;
    private LegacyMergerPatcher surfacePatcherWithoutCaves;

    @BeforeEach
    void setUp() throws Exception {
        blocks = new TestBlockFactory(AIR);
        surfaceWorld = worldReader("surface", "mca/surface.mca");
        cavesWorld = worldReader("caves", "mca/caves.mca");

        TestGenerationConfig absoluteConfig = new TestGenerationConfig(MINIMUM_Y, MAXIMUM_Y)
                .maximumCaveY(ABSOLUTE_MERGE_BOUNDARY_Y);
        GenerationContext absoluteContext = new GenerationContext(absoluteConfig, blocks);
        absolutePatcher = new LegacyMergerPatcher(new AbsoluteMerger(absoluteContext), surfaceWorld, cavesWorld);

        surfaceConfig = new TestGenerationConfig(MINIMUM_Y, MAXIMUM_Y)
                .maximumCaveY(MAXIMUM_Y)
                .mergeDepth(SURFACE_MERGE_DEPTH)
                .adaptiveMaximumDepth(ADAPTIVE_MAXIMUM_DEPTH)
                .adaptiveMinimumHiddenDepth(ADAPTIVE_MINIMUM_HIDDEN_DEPTH);
        IGNORED_SURFACE_BLOCKS.forEach(surfaceConfig::ignoreSurfaceBlock);
        GenerationContext surfaceContext = new GenerationContext(surfaceConfig, blocks);
        surfacePatcher = new LegacyMergerPatcher(new SurfaceMerger(surfaceWorld, surfaceContext),
                surfaceWorld, cavesWorld);
        surfacePatcherWithoutCaves = new LegacyMergerPatcher(new SurfaceMerger(surfaceWorld, surfaceContext),
                surfaceWorld, null);
    }

    @Test
    void regionFixturesExposeRealSurfaceAndCaveFeatures() {
        ChunkReader lowSurface = requiredChunk(surfaceWorld, new ChunkCoordinate(0, 0));
        ChunkReader caves = requiredChunk(cavesWorld, new ChunkCoordinate(0, 0));
        ChunkReader caveAir = requiredChunk(cavesWorld, new ChunkCoordinate(21, 16));
        ChunkReader mountain = requiredChunk(surfaceWorld, new ChunkCoordinate(23, 0));

        assertEquals("minecraft:stone", blockName(lowSurface, 0, 0, 0));
        assertEquals("minecraft:air", blockName(caves, 0, 0, 0));
        assertEquals("minecraft:cave_air", blockName(caveAir, 12, -62, 1));
        assertTrue(caveAir.blockAt(12, -62, 1).orElseThrow().isAir());
        assertEquals("minecraft:short_grass", blockName(mountain, 14, 174, 14));
    }

    @Test
    void absolutePatchedChunksMatchRealSurfaceAndCavesSourcesAtEveryCoordinate() {
        assertRepresentativeChunks("absolute merger", this::assertAbsolutePatchedChunk);
    }

    @Test
    void surfacePatchedChunksMatchTheSourceSelectedByEachColumnsBoundary() {
        assertRepresentativeChunks("surface merger with real caves",
                coordinate -> assertSurfacePatchedChunk(coordinate, surfacePatcher, true));
    }

    @Test
    void surfacePatchedChunksWithoutCavesLeaveTheUndergroundUntouched() {
        assertRepresentativeChunks("surface merger without caves",
                coordinate -> assertSurfacePatchedChunk(coordinate, surfacePatcherWithoutCaves, false));
    }

    private void assertAbsolutePatchedChunk(ChunkCoordinate coordinate) {
        ChunkReader surface = requiredChunk(surfaceWorld, coordinate);
        ChunkReader caves = requiredChunk(cavesWorld, coordinate);
        TestChunkGrid destination = new TestChunkGrid(coordinate.x(), coordinate.z(), MINIMUM_Y, MAXIMUM_Y,
                VOID, PLAINS);

        absolutePatcher.patch(destination);

        int surfaceAirBoundary = surface.airSectionsBottom();
        for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
            for (int y = MINIMUM_Y; y < MAXIMUM_Y; y++) {
                for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                    ExpectedBlock expected = expectedAbsoluteBlock(surface, caves, surfaceAirBoundary, x, y, z);
                    String actualName = destination.getBlock(x, y, z).getName();
                    if (!expected.name().equals(actualName)) {
                        fail("Chunk " + coordinate + " differs at local position " + x + ", " + y + ", " + z
                                + ": expected " + expected.name() + " from " + expected.source()
                                + " but was " + actualName);
                    }
                }
            }
        }
    }

    private void assertSurfacePatchedChunk(ChunkCoordinate coordinate, LegacyMergerPatcher mergerPatcher,
            boolean useCaves) {
        ChunkReader surface = requiredChunk(surfaceWorld, coordinate);
        ChunkReader caves = useCaves ? requiredChunk(cavesWorld, coordinate) : null;
        TestChunkGrid destination = new TestChunkGrid(coordinate.x(), coordinate.z(), MINIMUM_Y, MAXIMUM_Y,
                VOID, PLAINS);

        mergerPatcher.patch(destination);

        int surfaceAirBoundary = surface.airSectionsBottom();
        int[][] columnBoundaries = expectedSurfaceBoundaries(coordinate);
        int surfaceBlocks = 0;
        int caveBlocks = 0;
        int untouchedBlocks = 0;
        for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
            for (int y = MINIMUM_Y; y < MAXIMUM_Y; y++) {
                for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                    ExpectedBlock expected = expectedSurfaceBlock(surface, caves, surfaceAirBoundary,
                            columnBoundaries[x][z], x, y, z);
                    String actualName = destination.getBlock(x, y, z).getName();
                    if (!expected.name().equals(actualName)) {
                        fail("Chunk " + coordinate + " differs at local position " + x + ", " + y + ", " + z
                                + " with surface boundary " + columnBoundaries[x][z] + ": expected "
                                + expected.name() + " from " + expected.source() + " but was " + actualName);
                    }
                    if (expected.source() == BlockSource.UNTOUCHED_DESTINATION) {
                        untouchedBlocks++;
                    } else if (expected.source() == BlockSource.SURFACE) {
                        surfaceBlocks++;
                    } else if (expected.source() == BlockSource.CAVES) {
                        caveBlocks++;
                    }
                }
            }
        }
        assertTrue(surfaceBlocks > 0, "Expected blocks from the surface source in " + coordinate);
        if (useCaves) {
            assertTrue(caveBlocks > 0, "Expected blocks from the caves source in " + coordinate);
        }
        if (!useCaves) {
            assertTrue(untouchedBlocks > 0, "Expected untouched underground blocks in " + coordinate);
        }
    }

    private int[][] expectedSurfaceBoundaries(ChunkCoordinate coordinate) {
        int[][] boundaries = new int[GenerationConstants.CHUNK_SIZE][GenerationConstants.CHUNK_SIZE];
        for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                int globalX = coordinate.x() * GenerationConstants.CHUNK_SIZE + x;
                int globalZ = coordinate.z() * GenerationConstants.CHUNK_SIZE + z;
                boundaries[x][z] = expectedSurfaceBoundary(globalX, globalZ);
            }
        }
        return boundaries;
    }

    /** Independent oracle for the intended per-column Surface merger boundary. */
    private int expectedSurfaceBoundary(int globalX, int globalZ) {
        int minimumY = surfaceConfig.generationAreaMinY();
        int maximumCaveY = Math.max(minimumY,
                Math.min(surfaceConfig.maxHeightOfCaves(), surfaceConfig.generationAreaMaxY()));
        if (maximumCaveY <= minimumY) {
            return minimumY;
        }

        String biomeName = surfaceWorld.getBiomeName(globalX, surfaceConfig.generationAreaMaxY(), globalZ);
        if (surfaceConfig.isSurfaceWorldOnlyBiome(biomeName)) {
            return minimumY;
        }

        int surfaceY = maximumCaveY + surfaceConfig.mergeDepth();
        while (surfaceY > minimumY && !isExpectedSurfaceBlock(globalX, surfaceY, globalZ)) {
            surfaceY--;
        }

        int exposedBlocks = 0;
        int remainingAdaptiveDepth = surfaceConfig.adaptiveMaxMergeDepth() - surfaceConfig.mergeDepth();
        while (remainingAdaptiveDepth > 0 && surfaceY > minimumY
                && hasNonSolidHorizontalNeighbour(globalX, surfaceY - exposedBlocks, globalZ)) {
            exposedBlocks++;
            remainingAdaptiveDepth--;
        }
        int finalDepth = Math.max(surfaceConfig.mergeDepth(),
                exposedBlocks + surfaceConfig.adaptiveMinHiddenBlocksMergeDepth());
        return surfaceY - finalDepth;
    }

    private boolean isExpectedSurfaceBlock(int globalX, int y, int globalZ) {
        Block block = surfaceWorld.blockAt(globalX, y, globalZ).orElse(blocks.air());
        return block.isSolid() && !surfaceConfig.isIgnoredForSurfaceCalculation(block.getName());
    }

    private boolean hasNonSolidHorizontalNeighbour(int globalX, int y, int globalZ) {
        return isPresentAndNonSolid(surfaceWorld.blockAt(globalX + 1, y, globalZ))
                || isPresentAndNonSolid(surfaceWorld.blockAt(globalX - 1, y, globalZ))
                || isPresentAndNonSolid(surfaceWorld.blockAt(globalX, y, globalZ + 1))
                || isPresentAndNonSolid(surfaceWorld.blockAt(globalX, y, globalZ - 1));
    }

    private static boolean isPresentAndNonSolid(Optional<Block> block) {
        return block.isPresent() && !block.get().isSolid();
    }

    private static void assertRepresentativeChunks(String heading, Consumer<ChunkCoordinate> assertion) {
        List<Executable> chunkAssertions = REPRESENTATIVE_CHUNKS.stream()
                .<Executable>map(coordinate -> () -> assertion.accept(coordinate))
                .toList();
        assertAll(heading, chunkAssertions);
    }

    private ExpectedBlock expectedAbsoluteBlock(ChunkReader surface, ChunkReader caves, int surfaceAirBoundary,
            int x, int y, int z) {
        if (y >= surfaceAirBoundary) {
            return new ExpectedBlock(blocks.air().getName(), BlockSource.CLEARED_UPPER_REGION);
        }
        if (y > ABSOLUTE_MERGE_BOUNDARY_Y) {
            return new ExpectedBlock(blockNameOrAir(surface, x, y, z), BlockSource.SURFACE);
        }
        return new ExpectedBlock(blockNameOrAir(caves, x, y, z), BlockSource.CAVES);
    }

    private ExpectedBlock expectedSurfaceBlock(ChunkReader surface, ChunkReader caves, int surfaceAirBoundary,
            int columnBoundary, int x, int y, int z) {
        if (y >= surfaceAirBoundary) {
            return new ExpectedBlock(blocks.air().getName(), BlockSource.CLEARED_UPPER_REGION);
        }
        if (y > columnBoundary) {
            return new ExpectedBlock(blockNameOrAir(surface, x, y, z), BlockSource.SURFACE);
        }
        if (caves != null) {
            return new ExpectedBlock(blockNameOrAir(caves, x, y, z), BlockSource.CAVES);
        }
        return new ExpectedBlock(VOID.getName(), BlockSource.UNTOUCHED_DESTINATION);
    }

    private String blockNameOrAir(ChunkReader reader, int x, int y, int z) {
        return reader.blockAt(x, y, z).orElse(blocks.air()).getName();
    }

    private static String blockName(ChunkReader reader, int x, int y, int z) {
        return reader.blockAt(x, y, z).orElseThrow().getName();
    }

    private TestDiskWorldReader worldReader(String worldName, String resourcePath) throws Exception {
        Path worldDirectory = temporaryDirectory.resolve(worldName);
        Path regionDirectory = Files.createDirectories(worldDirectory.resolve("region"));
        Files.copy(resourcePath(resourcePath), regionDirectory.resolve("r.0.0.mca"), REPLACE_EXISTING);
        return new TestDiskWorldReader(regionDirectory.toFile(), 1, blocks);
    }

    private static Path resourcePath(String path) throws URISyntaxException {
        URL resource = MergerCharacterizationTest.class.getClassLoader().getResource(path);
        assertNotNull(resource, "Missing test resource " + path);
        return Path.of(resource.toURI());
    }

    private static ChunkReader requiredChunk(TestDiskWorldReader world, ChunkCoordinate coordinate) {
        return world.readChunk(coordinate.x(), coordinate.z()).orElseThrow(() ->
                new AssertionError("Missing source chunk " + coordinate));
    }

    private record ChunkCoordinate(int x, int z) {}

    private record ExpectedBlock(String name, BlockSource source) {}

    private enum BlockSource {
        SURFACE,
        CAVES,
        CLEARED_UPPER_REGION,
        UNTOUCHED_DESTINATION
    }
}
