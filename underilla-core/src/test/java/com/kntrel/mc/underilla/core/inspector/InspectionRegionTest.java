package com.kntrel.mc.underilla.core.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.generation.GenerationArea;
import org.junit.jupiter.api.Test;

class InspectionRegionTest {

    @Test
    void clampsBothEndsOfZSlice() {
        InspectionRegion requested = InspectionRegion.zSlice(0, -2, 5, -2, 6);

        InspectionRegion fitted = requested.clamp(new GenerationArea(-16, 0, 32, 16))
                .orElseThrow()
                .clamp(0, 4)
                .orElseThrow();

        assertEquals(InspectionRegion.Axis.Z, fitted.axis());
        assertEquals(-1, fitted.startChunk());
        assertEquals(3, fitted.chunkLength());
        assertEquals(0, fitted.minimumY());
        assertEquals(4, fitted.maximumY());
    }

    @Test
    void clampsXSliceToChunkOriginsCoveredByGenerationArea() {
        InspectionRegion fitted = InspectionRegion.xSlice(0, -2, 5, 0, 4)
                .clamp(new GenerationArea(0, -15, 16, 17))
                .orElseThrow();

        assertEquals(0, fitted.startChunk());
        assertEquals(2, fitted.chunkLength());
    }

    @Test
    void returnsEmptyForDisjointHorizontalOrVerticalRanges() {
        InspectionRegion region = InspectionRegion.zSlice(0, 0, 2, 0, 4);

        assertTrue(region.clamp(new GenerationArea(32, 0, 64, 16)).isEmpty());
        assertTrue(region.clamp(new GenerationArea(0, 16, 32, 32)).isEmpty());
        assertTrue(region.clamp(4, 8).isEmpty());
    }

    @Test
    void returnsAnEquivalentCopyWhenAlreadyContained() {
        InspectionRegion region = InspectionRegion.zSlice(0, 0, 2, 0, 4);

        InspectionRegion fitted = region.clamp(new GenerationArea(0, 0, 32, 16))
                .orElseThrow()
                .clamp(0, 4)
                .orElseThrow();

        assertEquals(region, fitted);
        assertNotSame(region, fitted);
    }
}
