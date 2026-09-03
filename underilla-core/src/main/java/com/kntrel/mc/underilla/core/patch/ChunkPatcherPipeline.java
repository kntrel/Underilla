package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.ChunkData;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Runs chunk patchers sequentially in their declared order. */
public final class ChunkPatcherPipeline implements ChunkPatcher {

    private final List<ChunkPatcher> patchers;

    public ChunkPatcherPipeline(ChunkPatcher... patchers) {
        this(Arrays.asList(patchers));
    }

    public ChunkPatcherPipeline(List<ChunkPatcher> patchers) {
        this.patchers = List.copyOf(patchers);
        this.patchers.forEach(patcher -> Objects.requireNonNull(patcher, "patcher"));
    }

    @Override
    public void patch(ChunkData targetChunk) {
        patchers.forEach(patcher -> patcher.patch(targetChunk));
    }
}
