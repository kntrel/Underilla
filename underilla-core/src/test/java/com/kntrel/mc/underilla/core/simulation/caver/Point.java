package com.kntrel.mc.underilla.core.simulation.caver;

/** An immutable point in the synthetic world's three-dimensional coordinate space. */
public record Point(double x, double y, double z) {

    public Point {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Point coordinates must be finite");
        }
    }
}
