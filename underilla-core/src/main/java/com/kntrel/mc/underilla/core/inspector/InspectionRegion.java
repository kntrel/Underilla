package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.GenerationConstants;
import java.util.Objects;

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

    /** Returns whether a chunk contributes one tile to this region. */
    public boolean contains(int chunkX, int chunkZ) {
        int fixedChunk = Math.floorDiv(coordinate, GenerationConstants.CHUNK_SIZE);
        int traversedChunk = axis == Axis.Z ? chunkX : chunkZ;
        return (axis == Axis.Z ? chunkZ : chunkX) == fixedChunk
                && traversedChunk >= startChunk
                && traversedChunk < Math.addExact(startChunk, chunkLength);
    }
}
