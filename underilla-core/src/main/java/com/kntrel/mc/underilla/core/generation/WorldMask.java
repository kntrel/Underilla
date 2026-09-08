package com.kntrel.mc.underilla.core.generation;

/** Selects the world positions that should come from the reference world. */
@FunctionalInterface
public interface WorldMask {

    boolean contains(int globalX, int y, int globalZ);
}
