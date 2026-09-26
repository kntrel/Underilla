package com.kntrel.mc.underilla.core.inspector;

/** Receives one immutable, complete frame after its expected chunks have all arrived. */
@FunctionalInterface
public interface InspectionSink {

    void publish(InspectionStage point, WorldSlice slice);
}
