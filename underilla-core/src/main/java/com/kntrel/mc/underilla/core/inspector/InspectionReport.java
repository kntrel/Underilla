package com.kntrel.mc.underilla.core.inspector;

import java.util.Map;

/** The completed frames of a successful inspection session. */
public record InspectionReport(Map<InspectionStage, WorldSlice> frames) {

    public InspectionReport {
        frames = Map.copyOf(frames);
    }
}
