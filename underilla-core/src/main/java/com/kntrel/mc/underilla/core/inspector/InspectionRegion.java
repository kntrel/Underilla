package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.generation.GenerationArea;
import java.util.Objects;
import java.util.Optional;

/** A finite vertical slice collected by an inspection session. */
public final class InspectionRegion {

    /** The horizontal axis held constant by the vertical slice. */
    public enum Axis {
        /** A constant-Z plane that traverses chunks along X. */
        Z,
        /** A constant-X plane that traverses chunks along Z. */
        X
    }

    private final Axis axis;
    private final int coordinate;
    private final int startChunk;
    private final int chunkLength;
    private final int minimumY;
    private final int maximumY;

    /**
     * Creates a vertical slice.
     *
     * @param axis the fixed world-coordinate axis
     * @param coordinate the fixed world X or Z coordinate
     * @param startChunk first chunk along the other horizontal axis
     * @param chunkLength number of chunks along that axis
     * @param minimumY inclusive lower Y bound
     * @param maximumY exclusive upper Y bound
     */
    public InspectionRegion(
            Axis axis,
            int coordinate,
            int startChunk,
            int chunkLength,
            int minimumY,
            int maximumY
    ) {
        this.axis = Objects.requireNonNull(axis, "axis");
        if (chunkLength < 1) {
            throw new IllegalArgumentException("chunkLength must be positive");
        }
        if (maximumY <= minimumY) {
            throw new IllegalArgumentException("maximumY must be greater than minimumY");
        }
        this.coordinate = coordinate;
        this.startChunk = startChunk;
        this.chunkLength = chunkLength;
        this.minimumY = minimumY;
        this.maximumY = maximumY;
    }

    public static InspectionRegion zSlice(
            int worldZ,
            int startChunkX,
            int chunkLength,
            int minimumY,
            int maximumY
    ) {
        return new InspectionRegion(Axis.Z, worldZ, startChunkX, chunkLength, minimumY, maximumY);
    }

    public static InspectionRegion xSlice(
            int worldX,
            int startChunkZ,
            int chunkLength,
            int minimumY,
            int maximumY
    ) {
        return new InspectionRegion(Axis.X, worldX, startChunkZ, chunkLength, minimumY, maximumY);
    }

    public Axis axis() { return axis; }

    /** The constant world X or Z coordinate, selected by {@link #axis()}. */
    public int coordinate() { return coordinate; }

    /** The first chunk along the slice's traversed horizontal axis. */
    public int startChunk() { return startChunk; }

    public int chunkLength() { return chunkLength; }

    public int minimumY() { return minimumY; }

    /** Exclusive maximum Y coordinate. */
    public int maximumY() { return maximumY; }

    /** Fits the slice to the chunks covered by the horizontal generation area. */
    public Optional<InspectionRegion> clamp(GenerationArea area) {
        Objects.requireNonNull(area, "area");
        boolean traversesX = axis == Axis.Z;
        long minimumFixedChunk = Math.ceilDiv(
                (long) (traversesX ? area.minimumZ() : area.minimumX()), GenerationConstants.CHUNK_SIZE);
        long maximumFixedChunk = Math.ceilDiv(
                (long) (traversesX ? area.maximumZ() : area.maximumX()), GenerationConstants.CHUNK_SIZE);
        long minimumTraversedChunk = Math.ceilDiv(
                (long) (traversesX ? area.minimumX() : area.minimumZ()), GenerationConstants.CHUNK_SIZE);
        long maximumTraversedChunk = Math.ceilDiv(
                (long) (traversesX ? area.maximumX() : area.maximumZ()), GenerationConstants.CHUNK_SIZE);
        int fixedChunk = Math.floorDiv(coordinate, GenerationConstants.CHUNK_SIZE);
        long fittedStart = Math.max((long) startChunk, minimumTraversedChunk);
        long fittedEnd = Math.min((long) startChunk + chunkLength, maximumTraversedChunk);
        if (fixedChunk < minimumFixedChunk || fixedChunk >= maximumFixedChunk || fittedStart >= fittedEnd) {
            return Optional.empty();
        }
        return Optional.of(new InspectionRegion(axis, coordinate, (int) fittedStart,
                (int) (fittedEnd - fittedStart), minimumY, maximumY));
    }

    /** Fits the slice to a maximum-exclusive vertical range. */
    public Optional<InspectionRegion> clamp(int rangeMinimumY, int rangeMaximumY) {
        int fittedMinimumY = Math.max(minimumY, rangeMinimumY);
        int fittedMaximumY = Math.min(maximumY, rangeMaximumY);
        if (fittedMinimumY >= fittedMaximumY) {
            return Optional.empty();
        }
        return Optional.of(new InspectionRegion(axis, coordinate, startChunk, chunkLength,
                fittedMinimumY, fittedMaximumY));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InspectionRegion region)) {
            return false;
        }
        return axis == region.axis && coordinate == region.coordinate
                && startChunk == region.startChunk && chunkLength == region.chunkLength
                && minimumY == region.minimumY && maximumY == region.maximumY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(axis, coordinate, startChunk, chunkLength, minimumY, maximumY);
    }

    @Override
    public String toString() {
        return "InspectionRegion[axis=" + axis + ", coordinate=" + coordinate
                + ", startChunk=" + startChunk + ", chunkLength=" + chunkLength
                + ", minimumY=" + minimumY + ", maximumY=" + maximumY + ']';
    }

    /** Returns whether a chunk contributes one tile to this region. */
    public boolean contains(int chunkX, int chunkZ) {
        int fixedChunk = Math.floorDiv(coordinate, GenerationConstants.CHUNK_SIZE);
        int traversedChunk = axis == Axis.Z ? chunkX : chunkZ;
        return (axis == Axis.Z ? chunkZ : chunkX) == fixedChunk
                && traversedChunk >= startChunk
                && traversedChunk < Math.addExact(startChunk, chunkLength);
    }
}
