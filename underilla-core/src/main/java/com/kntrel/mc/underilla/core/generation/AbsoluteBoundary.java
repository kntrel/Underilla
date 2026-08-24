package com.kntrel.mc.underilla.core.generation;

/** A boundary with one fixed Y value for every column. */
public final class AbsoluteBoundary implements Boundary {

    private final int boundaryY;

    public AbsoluteBoundary(int boundaryY, int minimumY, int maximumY) {
        this(Math.max(minimumY, Math.min(boundaryY, maximumY)));
    }

    public AbsoluteBoundary(int boundaryY) {
        this.boundaryY = boundaryY;
    }

    @Override
    public int at(int globalX, int globalZ) {
        return boundaryY;
    }
}
