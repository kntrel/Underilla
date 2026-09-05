package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.profiling.Tracker;
import java.util.Objects;

/** Decorates a chunk patcher with an individual {@code patch} measurement. */
public final class ChunkProfiledPatcher implements ChunkPatcher {

    private final ChunkPatcher patcher;
    private final Tracker tracker;

    public ChunkProfiledPatcher(ChunkPatcher patcher, Instrumenter instrumenter) {
        this.patcher = Objects.requireNonNull(patcher, "patcher");
        this.tracker = Objects.requireNonNull(instrumenter, "instrumenter").tracker(patcher.getClass());
    }

    @Override
    public void patch(ChunkData targetChunk) {
        try (var _ = tracker.stopwatch("patch")) {
            patcher.patch(targetChunk);
        }
    }
}
