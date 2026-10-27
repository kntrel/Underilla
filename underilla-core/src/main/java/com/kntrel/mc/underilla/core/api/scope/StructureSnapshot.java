package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.ID;

/** Read-only structure identity, placement origin, and bounds; unavailable spatial values are null. */
public record StructureSnapshot(ID id, Origin origin, Bounds bounds) {

    public StructureSnapshot(ID id, Origin origin) {
        this(id, origin, null);
    }

    /** Creates a type-only snapshot without a known placement origin. */
    public static StructureSnapshot of(ID id) {
        return new StructureSnapshot(id, null);
    }

    /** Captures the placement origin in global block coordinates. */
    public static StructureSnapshot of(ID id, int x, int y, int z) {
        return new StructureSnapshot(id, new Origin(x, y, z));
    }

    /** Captures the placement origin and its independently supplied bounding box. */
    public static StructureSnapshot of(ID id, int x, int y, int z, Bounds bounds) {
        return new StructureSnapshot(id, new Origin(x, y, z), bounds);
    }

    /** Immutable global block coordinates of the structure's placement origin. */
    public record Origin(int x, int y, int z) {}

    /**
     * Immutable axis-aligned bounds in global coordinates. Dimensions are geometric extents
     * ({@code max - min}), including empty space inside the box. Zero-sized extents are allowed.
     */
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Bounds {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
                throw new IllegalArgumentException("Bounds coordinates must be finite");
            }
            if (maxX < minX || maxY < minY || maxZ < minZ) {
                throw new IllegalArgumentException("Bounds maximum coordinates must not be below their minimums");
            }
        }

        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxY - minY;
        }

        public double depth() {
            return maxZ - minZ;
        }
    }
}
