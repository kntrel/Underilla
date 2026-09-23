package com.kntrel.mc.underilla.core.simulation.caver;

import java.util.Objects;

/** A cubic Bézier curve with explicit start, control, and end points. */
public final class BezierCurve {

    private static final double[] GAUSS_LEGENDRE_NODES = {
            -0.9739065285171717,
            -0.8650633666889845,
            -0.6794095682990244,
            -0.4333953941292472,
            -0.1488743389816312,
            0.1488743389816312,
            0.4333953941292472,
            0.6794095682990244,
            0.8650633666889845,
            0.9739065285171717
    };
    private static final double[] GAUSS_LEGENDRE_WEIGHTS = {
            0.0666713443086881,
            0.1494513491505806,
            0.2190863625159820,
            0.2692667193099964,
            0.2955242247147529,
            0.2955242247147529,
            0.2692667193099964,
            0.2190863625159820,
            0.1494513491505806,
            0.0666713443086881
    };

    private final Point start;
    private final Point firstControl;
    private final Point secondControl;
    private final Point end;
    private final double length;

    public BezierCurve(Point start, Point firstControl, Point secondControl, Point end) {
        this.start = Objects.requireNonNull(start, "start");
        this.firstControl = Objects.requireNonNull(firstControl, "firstControl");
        this.secondControl = Objects.requireNonNull(secondControl, "secondControl");
        this.end = Objects.requireNonNull(end, "end");
        length = calculateLength();
    }

    public Point start() {
        return start;
    }

    public Point firstControl() {
        return firstControl;
    }

    public Point secondControl() {
        return secondControl;
    }

    public Point end() {
        return end;
    }

    /** Returns the arc length calculated with ten-point Gauss-Legendre quadrature. */
    public double length() {
        return length;
    }

    /** Evaluates this curve at a progress from {@code 0.0} (start) to {@code 1.0} (end). */
    public Point at(double progress) {
        if (!Double.isFinite(progress) || progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("Curve progress must be finite and between 0.0 and 1.0");
        }
        return new Point(
                coordinate(start.x(), firstControl.x(), secondControl.x(), end.x(), progress),
                coordinate(start.y(), firstControl.y(), secondControl.y(), end.y(), progress),
                coordinate(start.z(), firstControl.z(), secondControl.z(), end.z(), progress));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BezierCurve other)) {
            return false;
        }
        return start.equals(other.start)
                && firstControl.equals(other.firstControl)
                && secondControl.equals(other.secondControl)
                && end.equals(other.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, firstControl, secondControl, end);
    }

    @Override
    public String toString() {
        return "BezierCurve[start=" + start
                + ", firstControl=" + firstControl
                + ", secondControl=" + secondControl
                + ", end=" + end + "]";
    }

    private double calculateLength() {
        double sum = 0.0;
        for (int index = 0; index < GAUSS_LEGENDRE_NODES.length; index++) {
            double progress = (GAUSS_LEGENDRE_NODES[index] + 1.0) * 0.5;
            sum += GAUSS_LEGENDRE_WEIGHTS[index] * speedAt(progress);
        }
        return sum * 0.5;
    }

    private double speedAt(double progress) {
        double inverseProgress = 1.0 - progress;
        double startWeight = 3.0 * inverseProgress * inverseProgress;
        double middleWeight = 6.0 * inverseProgress * progress;
        double endWeight = 3.0 * progress * progress;

        double x = startWeight * (firstControl.x() - start.x())
                + middleWeight * (secondControl.x() - firstControl.x())
                + endWeight * (end.x() - secondControl.x());
        double y = startWeight * (firstControl.y() - start.y())
                + middleWeight * (secondControl.y() - firstControl.y())
                + endWeight * (end.y() - secondControl.y());
        double z = startWeight * (firstControl.z() - start.z())
                + middleWeight * (secondControl.z() - firstControl.z())
                + endWeight * (end.z() - secondControl.z());
        return Math.hypot(x, Math.hypot(y, z));
    }

    private static double coordinate(double start, double firstControl, double secondControl, double end,
            double progress) {
        double inverseProgress = 1.0 - progress;
        return inverseProgress * inverseProgress * inverseProgress * start
                + 3.0 * inverseProgress * inverseProgress * progress * firstControl
                + 3.0 * inverseProgress * progress * progress * secondControl
                + progress * progress * progress * end;
    }
}
