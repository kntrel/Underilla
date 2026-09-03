package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.ChunkData;

/** Applies one transformation to a target chunk. */
@FunctionalInterface
public interface ChunkPatcher {

    void patch(ChunkData targetChunk);
}
