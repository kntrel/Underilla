package com.kntrel.mc.underilla.core.simulation.generator;

import com.kntrel.mc.underilla.core.simulation.caver.BezierCurve;
import com.kntrel.mc.underilla.core.simulation.caver.CaveCurve;
import com.kntrel.mc.underilla.core.simulation.caver.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Deterministically plans one cave path within a three-dimensional domain.
 *
 * <p>The domain is minimum-inclusive and maximum-exclusive on every axis. The supplied salt
 * distinguishes independently generated paths without changing the world's base seed.</p>
 */
final class CaveGenerator {

    private final Point minimum;
    private final Point maximum;
    private final long seed;
    private final double deviationFactor;
    private final double resolutionFactor;

    CaveGenerator(
            Point minimum,
            Point maximum,
            long seed,
            double deviationFactor,
            double resolutionFactor) {
        this.minimum = Objects.requireNonNull(minimum, "minimum");
        this.maximum = Objects.requireNonNull(maximum, "maximum");
        if (!hasFinitePositiveSize(minimum.x(), maximum.x())
                || !hasFinitePositiveSize(minimum.y(), maximum.y())
                || !hasFinitePositiveSize(minimum.z(), maximum.z())) {
            throw new IllegalArgumentException("Cave domain must have positive size on every axis");
        }
        if (!Double.isFinite(deviationFactor) || deviationFactor < 0.0) {
            throw new IllegalArgumentException("deviationFactor must be finite and non-negative");
        }
        if (!Double.isFinite(resolutionFactor) || resolutionFactor <= 0.0) {
            throw new IllegalArgumentException("resolutionFactor must be finite and positive");
        }
        this.seed = seed;
        this.deviationFactor = deviationFactor;
        this.resolutionFactor = resolutionFactor;
    }

    /**
     * Generates the cave curve identified by {@code salt}.
     *
     * <p>The resolution factor is interpreted as the desired number of Bézier segments per unit of
     * start-to-finish distance. Intermediate points are distributed uniformly along that vector,
     * then scattered along its two perpendicular axes.</p>
     *
     * @param salt stable per-cave value that selects a distinct deterministic path
     * @return the generated cave path
     */
    CaveCurve generate(long salt) {
        SplittableRandom random = new SplittableRandom(mix(seed ^ mix(salt)));

        // Choosing start and end points
        Point start = randomPoint(random);
        Point finish = randomPoint(random);

        // Defining distance and direction between points
        Vector direction = Vector.between(start, finish);
        double distance = direction.length();
        if (distance == 0.0) {
            throw new IllegalStateException("Cave domain could not provide two distinct points");
        }

        // Computing segments
        int segmentCount = segmentCount(distance);
        Vector forward = direction.scale(1.0 / distance);
        Vector reference = Math.abs(forward.y()) < 0.999
                ? new Vector(0.0, 1.0, 0.0)
                : new Vector(1.0, 0.0, 0.0);
        Vector localY = reference.subtract(forward.scale(reference.dot(forward))).normalized();
        Vector localZ = forward.cross(localY);

        List<Point> points = new ArrayList<>(segmentCount + 1);
        points.add(start);
        for (int index = 1; index < segmentCount; index++) {
            double progress = index / (double) segmentCount;
            double localX = distance * progress;
            double localYOffset = random.nextDouble() * deviationFactor;
            double localZOffset = random.nextDouble() * deviationFactor;
            points.add(transform(start, forward, localY, localZ,
                    localX, localYOffset, localZOffset));
        }
        points.add(finish);

        List<BezierCurve> segments = new ArrayList<>(segmentCount);
        for (int index = 0; index < segmentCount; index++) {
            Point segmentStart = points.get(index);
            Point segmentFinish = points.get(index + 1);
            segments.add(new BezierCurve(
                    segmentStart,
                    segmentStart,
                    segmentFinish,
                    segmentFinish));
        }
        return new CaveCurve(segments);
    }

    private Point randomPoint(SplittableRandom random) {
        return new Point(
                randomCoordinate(random, minimum.x(), maximum.x()),
                randomCoordinate(random, minimum.y(), maximum.y()),
                randomCoordinate(random, minimum.z(), maximum.z()));
    }

    private int segmentCount(double distance) {
        double requestedSegments = Math.ceil(distance * resolutionFactor);
        if (!Double.isFinite(requestedSegments) || requestedSegments > Integer.MAX_VALUE - 1.0) {
            throw new IllegalStateException("Cave resolution produces too many segments");
        }
        return Math.max(1, (int) requestedSegments);
    }

    private static Point transform(
            Point origin,
            Vector forward,
            Vector localY,
            Vector localZ,
            double x,
            double y,
            double z) {
        Vector offset = forward.scale(x).add(localY.scale(y)).add(localZ.scale(z));
        return new Point(origin.x() + offset.x(), origin.y() + offset.y(), origin.z() + offset.z());
    }

    private static double randomCoordinate(SplittableRandom random, double minimum, double maximum) {
        double coordinate = minimum + random.nextDouble() * (maximum - minimum);
        return coordinate < maximum ? coordinate : Math.nextDown(maximum);
    }

    private static boolean hasFinitePositiveSize(double minimum, double maximum) {
        return maximum > minimum && Double.isFinite(maximum - minimum);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record Vector(double x, double y, double z) {

        private static Vector between(Point start, Point finish) {
            return new Vector(
                    finish.x() - start.x(),
                    finish.y() - start.y(),
                    finish.z() - start.z());
        }

        private Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        private Vector subtract(Vector other) {
            return new Vector(x - other.x, y - other.y, z - other.z);
        }

        private Vector scale(double scalar) {
            return new Vector(x * scalar, y * scalar, z * scalar);
        }

        private double dot(Vector other) {
            return x * other.x + y * other.y + z * other.z;
        }

        private Vector cross(Vector other) {
            return new Vector(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        private double length() {
            return Math.sqrt(dot(this));
        }

        private Vector normalized() {
            return scale(1.0 / length());
        }
    }
}
