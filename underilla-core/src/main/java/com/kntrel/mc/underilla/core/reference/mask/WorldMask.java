package com.kntrel.mc.underilla.core.reference.mask;

import com.kntrel.mc.underilla.core.api.World;

/** Selects the world positions that should come from the reference world. */
@FunctionalInterface
public interface WorldMask {

    boolean contains(int globalX, int y, int globalZ);

    default boolean contains(World targetWorld, int globalX, int y, int globalZ) {
        return contains(globalX, y, globalZ);
    }

    default WorldMask inverted() {
        WorldMask original = this;
        return new WorldMask() {
            @Override
            public boolean contains(int x, int y, int z) {
                return !original.contains(x, y, z);
            }

            @Override
            public boolean contains(World targetWorld, int x, int y, int z) {
                return !original.contains(targetWorld, x, y, z);
            }
        };
    }
}
