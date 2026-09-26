package com.kntrel.mc.underilla.core.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.generation.Phase;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InspectorTest {

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock STONE = TestBlock.solid("minecraft:stone");
    private static final TestBlock DIRT = TestBlock.solid("minecraft:dirt");
    private static final BlockFactory BLOCKS = new BlockFactory() {
        @Override
        public Block air() {
            return AIR;
        }

        @Override
        public Block create(ID id) {
            return id.equals(AIR.id()) ? AIR : TestBlock.solid(id.toString());
        }
    };

    @TempDir
    Path output;

    @Test
    void waitsForEveryExpectedChunkAndIgnoresDuplicateCallbacks() throws IOException {
        TestWorld world = world();
        world.setBlock(0, 0, 0, STONE);
        world.setBlock(16, 0, 0, STONE);
        InspectionStage point = new InspectionStage(Phase.NOISE, InspectionStage.State.BEFORE_PATCH);
        Map<InspectionStage, WorldSlice> published = new LinkedHashMap<>();
        Inspector session = new Inspector(
                InspectionRegion.zSlice(0, 0, 2, 0, 4),
                Set.of(point),
                Runnable::run,
                published::put,
                null);

        session.capture(point, world.chunkData(1, 0).orElseThrow());
        assertFalse(session.completion().toCompletableFuture().isDone());

        session.capture(point, world.chunkData(1, 0).orElseThrow());
        session.capture(point, world.chunkData(0, 0).orElseThrow());

        assertTrue(session.completion().toCompletableFuture().isDone());
        assertEquals(1, published.size());
        WorldSlice frame = published.get(point);
        assertEquals(STONE, frame.getBlock(0, 0));
        assertEquals(STONE, frame.getBlock(16, 0));
    }

    @Test
    void decoratesPhasePatcherWithBeforeAndAfterCapture() throws IOException {
        TestWorld world = world();
        world.setBlock(0, 0, 0, AIR);
        InspectionStage before = new InspectionStage(Phase.NOISE, InspectionStage.State.BEFORE_PATCH);
        InspectionStage after = new InspectionStage(Phase.NOISE, InspectionStage.State.AFTER_PATCH);
        Map<InspectionStage, WorldSlice> published = new LinkedHashMap<>();
        Inspector session = new Inspector(
                InspectionRegion.zSlice(0, 0, 1, 0, 4),
                Set.of(before, after),
                Runnable::run,
                published::put,
                null);
        WorldGenerationPlan plan = WorldGenerationPlan.build()
                .afterNoise(chunk -> chunk.setBlock(0, 0, 0, STONE))
                .done();

        session.instrument(plan).tryAfterNoise(world.chunkData(0, 0).orElseThrow());

        session.completion().toCompletableFuture().join();
        assertEquals(AIR, published.get(before).getBlock(0, 0));
        assertEquals(STONE, published.get(after).getBlock(0, 0));
    }

    @Test
    void collectsAnXSliceAcrossZChunks() throws IOException {
        TestWorld world = world();
        world.setBlock(0, 0, 0, STONE);
        world.setBlock(0, 0, 16, STONE);
        InspectionStage point = InspectionStage.before(Phase.NOISE);
        Map<InspectionStage, WorldSlice> published = new LinkedHashMap<>();
        Inspector session = new Inspector(
                InspectionRegion.xSlice(0, 0, 2, 0, 4),
                Set.of(point),
                Runnable::run,
                published::put,
                null);

        session.capture(point, world.chunkData(0, 1).orElseThrow());
        session.capture(point, world.chunkData(0, 0).orElseThrow());

        WorldSlice frame = published.get(point);
        assertEquals(STONE, frame.getBlock(0, 0));
        assertEquals(STONE, frame.getBlock(16, 0));
    }

    @Test
    void capturesAWorldReaderXSliceInLocalCoordinates() {
        TestWorld world = world();
        world.setBlock(5, 2, -16, STONE);
        world.setBlock(5, 2, 0, DIRT);

        WorldSlice slice = WorldSlice.from(world, InspectionRegion.xSlice(5, -1, 2, 0, 4));

        assertEquals(STONE, slice.getBlock(0, 2));
        assertEquals(DIRT, slice.getBlock(16, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> slice.getBlock(-16, 2));
    }

    @Test
    void addressesBiomesByCellOrByLocalBlockCoordinates() {
        WorldSlice slice = new WorldSlice(-3, -3, 8, 8);
        slice.setBiome(0, 0, () -> ID.of("caves"));
        slice.setBiomeAt(3, 3, () -> ID.of("plains"));

        assertEquals(ID.of("caves"), slice.getBiome(0, 0).id());
        assertEquals(ID.of("caves"), slice.getBiomeAt(0, 0).id());
        assertEquals(ID.of("plains"), slice.getBiome(1, 1).id());
        assertEquals(ID.of("plains"), slice.getBiomeAt(3, 3).id());
    }

    @Test
    void resumesAnIncompleteFrameFromSavedChunkCaptures() throws IOException {
        TestWorld world = world();
        world.setBlock(0, 0, 0, STONE);
        world.setBlock(16, 0, 0, DIRT);
        InspectionRegion region = InspectionRegion.zSlice(0, 0, 2, 0, 4);
        InspectionStage point = InspectionStage.before(Phase.NOISE);
        InspectorPersister initialStore = new InspectorPersister(output, "world", "settings", region, Set.of(point), BLOCKS);
        Inspector interrupted = new Inspector(region, Set.of(point), Runnable::run, (_, _) -> {}, initialStore);

        interrupted.capture(point, world.chunkData(0, 0).orElseThrow());
        assertFalse(interrupted.completion().toCompletableFuture().isDone());
        assertEquals(1, initialStore.load().size());

        Map<InspectionStage, WorldSlice> published = new LinkedHashMap<>();
        InspectorPersister resumedStore = new InspectorPersister(output, "world", "settings", region, Set.of(point), BLOCKS);
        Inspector resumed = new Inspector(region, Set.of(point), Runnable::run, published::put, resumedStore);
        resumed.capture(point, world.chunkData(1, 0).orElseThrow());

        assertTrue(resumed.completion().toCompletableFuture().isDone());
        WorldSlice frame = published.get(point);
        assertEquals(STONE.id(), frame.getBlock(0, 0).id());
        assertEquals(DIRT.id(), frame.getBlock(16, 0).id());
    }

    private static TestWorld world() {
        return new TestWorld(0, 4, AIR, new TestBiome("minecraft:plains"));
    }
}
