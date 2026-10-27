package com.kntrel.mc.underilla.config.model;

import java.nio.file.Path;
import java.util.Objects;

public record DiagnosticsConfig(boolean debug, int progressIntervalSeconds, Inspection inspection) {
    public DiagnosticsConfig { Objects.requireNonNull(inspection, "inspection"); }

    public static DiagnosticsConfig defaults() {
        return new DiagnosticsConfig(false, 10, new Inspection(false, Axis.Z, 0, 0, 32,
                false, Path.of("./underilla-inspect")));
    }

    /** Intervals below one disable progress messages. */
    public boolean progressEnabled() { return progressIntervalSeconds >= 1; }

    public enum Axis { X, Z }

    public record Inspection(boolean enabled, Axis axis, int sliceCoordinate, int startChunk,
                             int lengthChunks, boolean biomeOverlay, Path output) {
        public Inspection {
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(output, "output");
            if (lengthChunks < 1) throw new IllegalArgumentException("inspection.length-chunks must be at least 1");
        }
    }
}
