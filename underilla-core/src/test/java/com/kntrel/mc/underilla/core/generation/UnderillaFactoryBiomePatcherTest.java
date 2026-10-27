package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.World;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.WorldSnapshot;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class UnderillaFactoryBiomePatcherTest {

    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");
    private static final TestBiome DEEP_DARK = new TestBiome("minecraft:deep_dark");
    private static final TestBiome REFERENCE = new TestBiome("example:reference");
    private static final TestBiome SURFACE_ONLY = new TestBiome("example:surface_only");
    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlockFactory BLOCKS = new TestBlockFactory(AIR);

    @Test
    void unavailableReferenceLeavesGeneratedBiomesUnchanged() {
        MutableBiomeData target = new MutableBiomeData(PLAINS, 0, 0, 0);

        patcher(new TestWorld(), 0, 4, 4, _ -> {}).patch(target);

        assertSame(PLAINS, target.get());
    }

    @Test
    void positionsOutsideTheGenerationAreaRemainUnchanged() {
        MutableBiomeData inside = new MutableBiomeData(PLAINS, 4, 0, 0);
        MutableBiomeData outside = new MutableBiomeData(PLAINS, 8, 0, 0);
        Patcher<BiomeData> patcher = patcher(
                referenceWorld(REFERENCE, 0, 4),
                0,
                4,
                4,
                builder -> builder.generationArea(0, 0, 8, 8)
        );

        patcher.patch(inside);
        patcher.patch(outside);

        assertSame(REFERENCE, inside.get());
        assertSame(PLAINS, outside.get());
    }

    @Test
    void referenceBiomesReplaceGeneratedBiomes() {
        MutableBiomeData target = new MutableBiomeData(PLAINS, 0, 0, 0);

        patcher(referenceWorld(REFERENCE, 0, 4), 0, 4, 4, _ -> {}).patch(target);

        assertSame(REFERENCE, target.get());
    }

    @Test
    void selectedGeneratedBiomesArePreservedOnlyOutsideTheMask() {
        MutableBiomeData outsideMask = new MutableBiomeData(DEEP_DARK, 0, 32, 0);
        MutableBiomeData intersectingMask = new MutableBiomeData(DEEP_DARK, 0, 36, 0);
        Patcher<BiomeData> patcher = patcher(
                referenceWorld(REFERENCE, 32, 40),
                32,
                40,
                32,
                builder -> builder
                        .preservedGeneratedBiomes(snapshot -> DEEP_DARK.id().equals(snapshot.target().biome().id()))
                        .preserveGeneratedBiomesOnlyUnderSurface(true)
        );

        patcher.patch(outsideMask);
        patcher.patch(intersectingMask);

        assertSame(DEEP_DARK, outsideMask.get());
        assertSame(REFERENCE, intersectingMask.get());
    }

    @Test
    void surfaceOnlyReferenceBiomesOverrideGeneratedBiomePreservation() {
        MutableBiomeData target = new MutableBiomeData(DEEP_DARK, 0, 0, 0);
        Patcher<BiomeData> patcher = patcher(
                referenceWorld(SURFACE_ONLY, 0, 4),
                0,
                4,
                4,
                builder -> builder
                        .surfaceOnlyBiomes(snapshot -> SURFACE_ONLY.id().equals(snapshot.source().biome().id()))
                        .preservedGeneratedBiomes(snapshot -> DEEP_DARK.id().equals(snapshot.target().biome().id()))
        );

        patcher.patch(target);

        assertSame(SURFACE_ONLY, target.get());
    }

    @Test
    void biomePatcherComposesSnapshotPoliciesWithSurfaceOverridesTakingPrecedence() {
        MutableBiomeData target = new MutableBiomeData(DEEP_DARK, 0, 0, 0);
        Patcher<BiomeData> patcher = Patchers.referenceWorldBiomePatcher(
                referenceWorld(REFERENCE, 0, 4), GenerationArea.everywhere(), 4, false,
                (_, _, _) -> { throw new AssertionError("Surface overrides must not consult the mask"); },
                snapshot -> snapshot.source().biome().id().equals(REFERENCE.id()) && snapshot.target().biome().id().equals(DEEP_DARK.id()),
                _ -> { throw new AssertionError("Surface overrides must take precedence over preservation"); },
                true);

        patcher.patch(target);

        assertSame(REFERENCE, target.get());
    }

    @Test
    void biomePoliciesReceiveTheDistinctSourceAndTargetWorldMetadata() {
        World source = World.of("source", ID.of("minecraft:the_nether"), 0, 256);
        World destination = World.of("destination", ID.of("minecraft:overworld"), -64, 320);
        MutableBiomeData target = new MutableBiomeData(DEEP_DARK, 0, 1, 0, destination);
        Patcher<BiomeData> patcher = patcher(
                referenceWorld(REFERENCE, 0, 4).withWorld(source), 0, 4, 4,
                builder -> builder.preservedGeneratedBiomes(snapshot -> {
                    assertEquals(source.name(), snapshot.source().world().name());
                    assertEquals(source.dimension(), snapshot.source().world().dimension());
                    assertEquals(WorldSnapshot.of(destination), snapshot.target().world());
                    assertEquals(-64, snapshot.target().world().minHeight());
                    return snapshot.source().world().dimension().equals(ID.of("minecraft:the_nether"))
                            && snapshot.target().world().name().equals("destination");
                }));

        patcher.patch(target);

        assertSame(DEEP_DARK, target.get());
    }

    @Test
    void biomePatcherSuppliesSharedChunkSnapshotWithoutLoadingChunks() {
        WorldReader referenceWorld = new WorldReader() {
            @Override public String name() { return "test-world"; }
            @Override public com.kntrel.mc.underilla.core.api.ID dimension() { return com.kntrel.mc.underilla.core.api.ID.of("minecraft:overworld"); }
            @Override public long seed() { return 0L; }
            @Override public int minHeight() { return -64; }
            @Override public int maxHeight() { return 320; }

            @Override
            public Optional<Block> blockAt(int x, int y, int z) {
                throw new AssertionError("Biome selection must not read blocks");
            }

            @Override
            public Optional<Biome> biomeAt(int x, int y, int z) {
                return Optional.of(REFERENCE);
            }

            @Override
            public Optional<ChunkReader> readChunk(int x, int z) {
                throw new AssertionError("Coordinate views must not load chunks");
            }
        };
        MutableBiomeData target = new MutableBiomeData(DEEP_DARK, -1, 1, -17);
        Patcher<BiomeData> patcher = Patchers.referenceWorldBiomePatcher(
                referenceWorld, GenerationArea.everywhere(), 4, false, (_, _, _) -> false,
                snapshot -> {
                    assertEquals(-1, snapshot.source().chunk().x());
                    assertEquals(-2, snapshot.source().chunk().z());
                    return snapshot.source().biome().id().equals(REFERENCE.id()) && snapshot.target().biome().id().equals(DEEP_DARK.id());
                },
                _ -> true, false);

        patcher.patch(target);

        assertSame(REFERENCE, target.get());
    }

    @Test
    void biomePatcherChecksTheGenerationAreaBeforeEvaluatingSnapshotPolicies() {
        MutableBiomeData target = new MutableBiomeData(PLAINS, 8, 0, 0);
        Patcher<BiomeData> patcher = Patchers.referenceWorldBiomePatcher(
                referenceWorld(REFERENCE, 0, 4), new GenerationArea(0, 0, 8, 8), 4, false,
                (_, _, _) -> { throw new AssertionError("Outside positions must not consult the mask"); },
                _ -> { throw new AssertionError("Outside positions must not evaluate source rules"); },
                _ -> { throw new AssertionError("Outside positions must not evaluate target rules"); },
                true);

        patcher.patch(target);

        assertSame(PLAINS, target.get());
    }

    @Test
    void preservationCanCompareBothBiomesAndChunkCoordinatesAtNegativePositions() {
        TestChunkGrid reference = new TestChunkGrid(-1, -2, 0, 4, AIR, REFERENCE);
        MutableBiomeData target = new MutableBiomeData(DEEP_DARK, -1, 1, -17);
        Patcher<BiomeData> patcher = patcher(
                new TestWorld().addChunk(reference), 0, 4, 4,
                builder -> builder.preservedGeneratedBiomes(snapshot -> {
                    assertEquals(-1, snapshot.source().chunk().x());
                    assertEquals(-2, snapshot.source().chunk().z());
                    assertEquals(-1, snapshot.source().biome().cellX());
                    assertEquals(0, snapshot.source().biome().cellY());
                    assertEquals(-5, snapshot.source().biome().cellZ());
                    assertEquals(snapshot.source().biome().cellX(), snapshot.target().biome().cellX());
                    assertEquals(snapshot.source().biome().cellY(), snapshot.target().biome().cellY());
                    assertEquals(snapshot.source().biome().cellZ(), snapshot.target().biome().cellZ());
                    return snapshot.source().biome().id().equals(REFERENCE.id()) && snapshot.target().biome().id().equals(DEEP_DARK.id());
                }));

        patcher.patch(target);

        assertSame(DEEP_DARK, target.get());
    }

    @Test
    void topYPolicySamplesOneReferenceLayerForEveryBiome() {
        TestChunkGrid referenceChunk = chunk(0, 5, PLAINS);
        referenceChunk.fillBiomeLayer(4, REFERENCE);
        MutableBiomeData target = new MutableBiomeData(PLAINS, 0, 0, 0);
        Patcher<BiomeData> patcher = patcher(
                new TestWorld().addChunk(referenceChunk),
                0,
                4,
                4,
                builder -> builder.surfaceBiomeUseTopYOnly(true).surfaceOnlyBiomes(snapshot -> {
                    assertEquals(REFERENCE.id(), snapshot.source().biome().id());
                    assertEquals(PLAINS.id(), snapshot.target().biome().id());
                    assertEquals(1, snapshot.source().biome().cellY());
                    assertEquals(0, snapshot.target().biome().cellY());
                    return false;
                })
        );

        patcher.patch(target);

        assertSame(REFERENCE, target.get());
    }

    private static Patcher<BiomeData> patcher(
            TestWorld referenceWorld,
            int minimumY,
            int maximumY,
            int maximumCaveY,
            Consumer<UnderillaFactory.Builder> configure
    ) {
        UnderillaFactory.Builder builder = UnderillaFactory.absolute(referenceWorld)
                .verticalRange(minimumY, maximumY)
                .maximumCaveY(maximumCaveY)
                .blocks(BLOCKS);
        configure.accept(builder);
        return builder.build().biomePatch();
    }

    private static TestWorld referenceWorld(TestBiome biome, int minimumY, int maximumY) {
        return new TestWorld().addChunk(chunk(minimumY, maximumY, biome));
    }

    private static TestChunkGrid chunk(int minimumY, int maximumY, TestBiome biome) {
        return new TestChunkGrid(0, 0, minimumY, maximumY, AIR, biome);
    }

    private static final class MutableBiomeData implements BiomeData {
        private Biome biome;
        private final int x;
        private final int y;
        private final int z;
        private final World world;

        private MutableBiomeData(Biome biome, int x, int y, int z) {
            this(biome, x, y, z, World.of("test-target", ID.of("minecraft:overworld"), -64, 320));
        }

        private MutableBiomeData(Biome biome, int x, int y, int z, World world) {
            this.biome = biome;
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
        }

        @Override
        public World world() { return world; }

        @Override
        public Biome get() { return biome; }

        @Override
        public void set(Biome biome) { this.biome = biome; }

        @Override
        public int getX() { return x; }

        @Override
        public int getY() { return y; }

        @Override
        public int getZ() { return z; }
    }
}
