package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.patch.BiomePatcher;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.profiling.Measurement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldGenerationPlanTest {

    @Test
    void builderComposesPatchersAtEachPhaseInDeclaredOrder() {
        List<String> calls = new ArrayList<>();
        List<Measurement> measurements = new ArrayList<>();
        ChunkPatcher noiseFirst = ignored -> calls.add("noise-first");
        ChunkPatcher noiseSecond = ignored -> calls.add("noise-second");
        ChunkPatcher surface = ignored -> calls.add("surface");
        ChunkPatcher carvers = ignored -> calls.add("carvers");
        ChunkPatcher features = ignored -> calls.add("features");
        ChunkPatcher load = ignored -> calls.add("load");

        WorldGenerationPlan plan = WorldGenerationPlan.build()
                .instrumenter(new Instrumenter(measurements::add))
                .afterNoise(noiseFirst, noiseSecond)
                .afterSurface(surface)
                .afterCarvers(carvers)
                .afterFeatures(features)
                .afterLoad(load)
                .noise(false)
                .carvers(false)
                .done();

        plan.afterNoise().patch(null);
        plan.afterSurface().patch(null);
        plan.afterCarvers().patch(null);
        plan.afterFeatures().patch(null);
        plan.afterLoad().patch(null);

        assertEquals(List.of("noise-first", "noise-second", "surface", "carvers", "features", "load"), calls);
        assertFalse(plan.flags().noise());
        assertFalse(plan.flags().carvers());
        assertTrue(plan.flags().surface());
        assertTrue(plan.flags().features());
        assertEquals(6, measurements.size());
        assertTrue(measurements.stream().allMatch(measurement -> measurement.event().equals("patch")));
    }

    @Test
    void defaultsAreSafeForAnAdapterToInvoke() {
        WorldGenerationPlan plan = WorldGenerationPlan.build().done();

        assertTrue(plan.coverage().covers(Integer.MIN_VALUE, Integer.MAX_VALUE));
        plan.afterNoise().patch(null);
        plan.afterSurface().patch(null);
        plan.afterCarvers().patch(null);
        plan.afterFeatures().patch(null);
        plan.afterLoad().patch(null);

        assertFalse(plan.biomePatch().patch(null));
        assertEquals(0, plan.altimeter().heightAt(null, 0, 0, null));
        assertEquals(new GenerationFlags(true, true, true, true, true, true), plan.flags());
    }

    @Test
    void configuredCoverageIsExposedToTheAdapter() {
        WorldGenerationPlan plan = WorldGenerationPlan.build()
                .coverage((chunkX, chunkZ) -> chunkX == 2 && chunkZ == -3)
                .done();

        assertTrue(plan.coverage().covers(2, -3));
        assertFalse(plan.coverage().covers(2, -2));
    }

    @Test
    void tryPhaseUtilitiesOnlyInvokePatchersForCoveredChunks() {
        List<String> calls = new ArrayList<>();
        WorldGenerationPlan plan = WorldGenerationPlan.build()
                .coverage((chunkX, chunkZ) -> chunkX == 2 && chunkZ == -3)
                .afterNoise(_ -> calls.add("noise"))
                .afterSurface(_ -> calls.add("surface"))
                .afterCarvers(_ -> calls.add("carvers"))
                .afterFeatures(_ -> calls.add("features"))
                .afterLoad(_ -> calls.add("load"))
                .done();
        TestBlock air = TestBlock.air("minecraft:air");
        TestBiome plains = new TestBiome("minecraft:plains");
        TestChunkGrid covered = new TestChunkGrid(2, -3, 0, 1, air, plains);
        TestChunkGrid uncovered = new TestChunkGrid(2, -2, 0, 1, air, plains);

        assertTrue(plan.tryAfterNoise(covered));
        assertTrue(plan.tryAfterSurface(covered));
        assertTrue(plan.tryAfterCarvers(covered));
        assertTrue(plan.tryAfterFeatures(covered));
        assertTrue(plan.tryAfterLoad(covered));
        assertFalse(plan.tryAfterNoise(uncovered));
        assertFalse(plan.tryAfterSurface(uncovered));
        assertFalse(plan.tryAfterCarvers(uncovered));
        assertFalse(plan.tryAfterFeatures(uncovered));
        assertFalse(plan.tryAfterLoad(uncovered));

        assertEquals(List.of("noise", "surface", "carvers", "features", "load"), calls);
    }

    @Test
    void instrumenterProfilesBiomeAndHeightQueries() {
        List<Measurement> measurements = new ArrayList<>();
        BiomePatcher biomePatcher = ignored -> true;
        Altimeter altimeter = (worldInfo, x, z, heightMap) -> 42;
        WorldGenerationPlan plan = WorldGenerationPlan.build()
                .instrumenter(new Instrumenter(measurements::add))
                .biomePatch(biomePatcher)
                .altimeter(altimeter)
                .done();

        assertTrue(plan.biomePatch().patch(null));
        assertEquals(42, plan.altimeter().heightAt(null, 0, 0, null));

        assertEquals(2, measurements.size());
        assertEquals(biomePatcher.getClass(), measurements.get(0).subject());
        assertEquals("patch", measurements.get(0).event());
        assertEquals(altimeter.getClass(), measurements.get(1).subject());
        assertEquals("height_at", measurements.get(1).event());
    }
}
