package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.ChunkData;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Runs patchers sequentially in their declared order. */
public final class PatcherPipeline implements Patcher {

    private final List<Patcher> patchers;

    public PatcherPipeline(Patcher... patchers) {
        this(Arrays.asList(patchers));
    }

    public PatcherPipeline(List<Patcher> patchers) {
        this.patchers = List.copyOf(patchers);
        this.patchers.forEach(patcher -> Objects.requireNonNull(patcher, "patcher"));
    }

    @Override
    public void patch(ChunkData targetChunk) {
        patchers.forEach(patcher -> patcher.patch(targetChunk));
    }
}
