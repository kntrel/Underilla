package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        plan.afterNoise().patch(null);
        plan.afterSurface().patch(null);
        plan.afterCarvers().patch(null);
        plan.afterFeatures().patch(null);
        plan.afterLoad().patch(null);

        assertFalse(plan.biomePatch().patch(null));
        assertEquals(0, plan.altimeter().baseHeight(null, 0, 0, null));
        assertEquals(new GenerationFlags(true, true, true, true, true, true), plan.flags());
    }
}
