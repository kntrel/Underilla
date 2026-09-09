package com.kntrel.mc.underilla.core.reference.mask;

/** Selects the world positions that should come from the reference world. */
@FunctionalInterface
public interface WorldMask {

    boolean contains(int globalX, int y, int globalZ);

    default WorldMask inverted() {
        return (x, y, z) -> !this.contains(x, y, z);
    }
}
