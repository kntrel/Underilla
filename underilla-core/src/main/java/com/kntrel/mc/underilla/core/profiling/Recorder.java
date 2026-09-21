package com.kntrel.mc.underilla.core.profiling;

/** Receives completed instrumentation measurements. */
@FunctionalInterface
public interface Recorder {

    /** Records one completed measurement. */
    void record(Measurement measurement);
}
