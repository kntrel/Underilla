package com.kntrel.mc.underilla.core.generation;

/** Determines whether an Underilla generation plan covers a chunk. */
@FunctionalInterface
public interface ChunkCoverage {

    boolean covers(int chunkX, int chunkZ);
}
