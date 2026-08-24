package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.ChunkData;

/** Applies one generation transformation to a target chunk. */
@FunctionalInterface
public interface Patcher {

    void patch(ChunkData targetChunk);
}
