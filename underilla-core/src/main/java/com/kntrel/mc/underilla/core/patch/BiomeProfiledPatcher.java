package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.profiling.Tracker;
import java.util.Objects;

/** Decorates a biome patcher with an individual {@code patch} measurement. */
public final class BiomeProfiledPatcher implements BiomePatcher {

    private final BiomePatcher patcher;
    private final Tracker tracker;

    public BiomeProfiledPatcher(BiomePatcher patcher, Instrumenter instrumenter) {
        this.patcher = Objects.requireNonNull(patcher, "patcher");
        this.tracker = Objects.requireNonNull(instrumenter, "instrumenter").tracker(patcher.getClass());
    }

    @Override
    public boolean patch(BiomeData biomeData) {
        try (var _ = tracker.stopwatch("patch")) {
            return patcher.patch(biomeData);
        }
    }
}
