package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.UnderillaEngine;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.profiling.Measurement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnderillaEngineProfilingTest {

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");

    @Test
    void recordsEachPatcherAndKeepsOneOperationPerEngineCall() {
        List<Measurement> measurements = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        Instrumenter instrumenter = new Instrumenter(measurements::add);
        PatchingPlan plan = new PatchingPlan(
                List.of(new CaveStep(calls), new SurfaceStep(calls)),
                new LiquidStep(calls),
                new AbsoluteBoundary(0),
                true);
        TestWorld referenceWorld = new TestWorld().addChunk(chunk());
        GenerationContext context = new GenerationContext(
                new TestGenerationConfig(0, 1),
                new TestBlockFactory(AIR));
        UnderillaEngine engine = new UnderillaEngine(referenceWorld, plan, context, instrumenter);

        assertTrue(engine.tryPatchTerrain(chunk()));
        assertTrue(engine.tryPatchLiquids(chunk()));

        assertEquals(List.of("cave", "surface", "liquid"), calls);
        assertEquals(5, measurements.size());
        assertMeasurement(measurements.get(0), CaveStep.class, "patch");
        assertMeasurement(measurements.get(1), SurfaceStep.class, "patch");
        assertMeasurement(measurements.get(2), UnderillaEngine.class, "terrain_patch");
        assertMeasurement(measurements.get(3), LiquidStep.class, "patch");
        assertMeasurement(measurements.get(4), UnderillaEngine.class, "liquid_patch");
        assertEquals(1, measurements.subList(0, 3).stream().map(Measurement::operationId).distinct().count());
        assertEquals(1, measurements.subList(3, 5).stream().map(Measurement::operationId).distinct().count());
    }

    private static TestChunkGrid chunk() {
        return new TestChunkGrid(0, 0, 0, 1, AIR, PLAINS);
    }

    private static void assertMeasurement(Measurement measurement, Class<?> subject, String event) {
        assertEquals(subject, measurement.subject());
        assertEquals(event, measurement.event());
    }

    private abstract static class RecordingPatcher implements Patcher {

        private final List<String> calls;
        private final String name;

        private RecordingPatcher(List<String> calls, String name) {
            this.calls = calls;
            this.name = name;
        }

        @Override
        public void patch(ChunkData targetChunk) {
            calls.add(name);
        }
    }

    private static final class CaveStep extends RecordingPatcher {
        private CaveStep(List<String> calls) { super(calls, "cave"); }
    }

    private static final class SurfaceStep extends RecordingPatcher {
        private SurfaceStep(List<String> calls) { super(calls, "surface"); }
    }

    private static final class LiquidStep extends RecordingPatcher {
        private LiquidStep(List<String> calls) { super(calls, "liquid"); }
    }
}
