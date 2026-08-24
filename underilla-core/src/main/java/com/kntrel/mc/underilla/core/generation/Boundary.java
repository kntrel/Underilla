package com.kntrel.mc.underilla.core.generation;

/** Resolves the vertical boundary separating reference terrain from underground terrain. */
public interface Boundary {

    int at(int globalX, int globalZ);

    default boolean isAbove(int globalX, int y, int globalZ) {
        return y > at(globalX, globalZ);
    }

    default boolean isAboveEquals(int globalX, int y, int globalZ) {
        return y >= at(globalX, globalZ);
    }

    default boolean isBelow(int globalX, int y, int globalZ) {
        return y < at(globalX, globalZ);
    }

    default boolean isBelowEquals(int globalX, int y, int globalZ) {
        return y <= at(globalX, globalZ);
    }
}
