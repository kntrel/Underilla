package com.kntrel.mc.underilla.core.simulation.caver;

import java.util.List;
import java.util.Objects;

/** An ordered collection of cubic Bézier segments describing a cave path. */
public final class CaveCurve {

    private final List<BezierCurve> segments;
    private final double length;

    public CaveCurve(List<BezierCurve> segments) {
        this.segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (this.segments.isEmpty()) {
            throw new IllegalArgumentException("A cave curve requires at least one Bézier segment");
        }
        for (int index = 1; index < this.segments.size(); index++) {
            BezierCurve previous = this.segments.get(index - 1);
            BezierCurve current = this.segments.get(index);
            if (!previous.end().equals(current.start())) {
                throw new IllegalArgumentException("Adjacent Bézier segments must share an endpoint");
            }
        }
        length = this.segments.stream().mapToDouble(BezierCurve::length).sum();
    }

    public List<BezierCurve> segments() {
        return segments;
    }

    /** Returns the cached sum of every Bézier segment's arc length. */
    public double length() {
        return length;
    }

    /**
     * Evaluates this curve at a progress from {@code 0.0} (the first segment's start) to
     * {@code 1.0} (the last segment's end), allocated in proportion to segment length.
     */
    public Point at(double progress) {
        requireProgress(progress);
        double remainingDistance = progress * length;
        for (int index = 0; index < segments.size(); index++) {
            BezierCurve segment = segments.get(index);
            double segmentLength = segment.length();
            if (remainingDistance <= segmentLength || index == segments.size() - 1) {
                double segmentProgress = segmentLength == 0.0 ? 1.0 : remainingDistance / segmentLength;
                return segment.at(Math.min(segmentProgress, 1.0));
            }
            remainingDistance -= segmentLength;
        }
        throw new AssertionError("A cave curve must have at least one segment");
    }

    /** Returns the number of Bézier segments in this curve. */
    public int segmentCount() {
        return segments.size();
    }

    /** Evaluates one Bézier segment at a progress from {@code 0.0} to {@code 1.0}. */
    public Point at(int segment, double progress) {
        if (segment < 0 || segment >= segmentCount()) {
            throw new IndexOutOfBoundsException("Curve segment outside range: " + segment);
        }
        requireProgress(progress);
        return segments.get(segment).at(progress);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof CaveCurve other && segments.equals(other.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        return "CaveCurve[segments=" + segments + "]";
    }

    private static void requireProgress(double progress) {
        if (!Double.isFinite(progress) || progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("Curve progress must be finite and between 0.0 and 1.0");
        }
    }
}
