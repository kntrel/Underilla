package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.HeightMapType;
import com.kntrel.mc.underilla.core.api.WorldInfo;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.profiling.Tracker;
import java.util.Objects;

/** Decorates an altimeter with an individual {@code height_at} measurement. */
public final class ProfiledAltimeter implements Altimeter {

    private final Altimeter altimeter;
    private final Tracker tracker;

    public ProfiledAltimeter(Altimeter altimeter, Instrumenter instrumenter) {
        this.altimeter = Objects.requireNonNull(altimeter, "altimeter");
        this.tracker = Objects.requireNonNull(instrumenter, "instrumenter").tracker(altimeter.getClass());
    }

    @Override
    public int heightAt(WorldInfo worldInfo, int x, int z, HeightMapType heightMap) {
        try (var _ = tracker.stopwatch("height_at")) {
            return altimeter.heightAt(worldInfo, x, z, heightMap);
        }
    }
}
