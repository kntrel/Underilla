package com.kntrel.mc.underilla.core.generation;

/** Selects every position above one fixed Y value. */
public final class AbsoluteWorldMask implements WorldMask {

    private final int boundaryY;

    public AbsoluteWorldMask(int boundaryY, int minimumY, int maximumY) {
        this(Math.max(minimumY, Math.min(boundaryY, maximumY)));
    }

    public AbsoluteWorldMask(int boundaryY) {
        this.boundaryY = boundaryY;
    }

    @Override
    public boolean contains(int globalX, int y, int globalZ) {
        return y > boundaryY;
    }
}
