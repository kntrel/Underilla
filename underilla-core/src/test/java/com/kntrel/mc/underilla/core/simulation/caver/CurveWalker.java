package com.kntrel.mc.underilla.core.simulation.caver;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Lazily iterates points along a cave curve at a fixed world-space distance. */
public final class CurveWalker implements Iterable<Point>, Iterator<Point> {

    private final CaveCurve curve;
    private final double stepSize;
    private double travelledDistance;
    private boolean finished;

    /**
     * Creates a walker that starts at the curve's first point and always includes its final point.
     *
     * @param curve curve to walk
     * @param stepSize world-space distance to advance after each yielded point; must be finite and
     *        positive
     */
    public CurveWalker(CaveCurve curve, double stepSize) {
        this.curve = Objects.requireNonNull(curve, "curve");
        if (!Double.isFinite(stepSize) || stepSize <= 0.0) {
            throw new IllegalArgumentException("stepSize must be finite and positive");
        }
        this.stepSize = stepSize;
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }

    @Override
    public Iterator<Point> iterator() {
        return new CurveWalker(curve, stepSize);
    }

    @Override
    public Point next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        double length = curve.length();
        double progress = length == 0.0 ? 0.0 : travelledDistance / length;
        Point point = curve.at(progress);
        if (travelledDistance >= length) {
            finished = true;
            return point;
        }

        double nextDistance = Math.min(length, travelledDistance + stepSize);
        travelledDistance = nextDistance > travelledDistance ? nextDistance : length;
        return point;
    }
}
