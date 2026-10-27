package com.kntrel.mc.underilla.config.model;

import com.kntrel.mc.underilla.config.context.BlockContext;
import com.kntrel.mc.underilla.config.context.DualBlockContext;
import java.util.Objects;

public record MergeConfig(Strategy strategy, Absolute absolute, Surface surface, Selector<DualBlockContext> keepSourceIf) {
    public MergeConfig {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(absolute, "absolute");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(keepSourceIf, "keep-source-if");
    }

    public enum Strategy { SURFACE, ABSOLUTE, NONE }

    public record Absolute(int boundary, int depth) {
        public Absolute {
            if (depth < 0) throw new IllegalArgumentException("absolute.depth must not be negative");
        }
    }

    public record Surface(int depth, AdaptiveDepth adaptiveDepth, Selector<BlockContext> passthroughBlockIf) {
        public Surface {
            if (depth < 0) throw new IllegalArgumentException("surface.depth must not be negative");
            Objects.requireNonNull(adaptiveDepth, "adaptive-depth");
            Objects.requireNonNull(passthroughBlockIf, "passthrough-block-if");
            if (adaptiveDepth.minHiddenDepth() > depth) {
                throw new IllegalArgumentException("min-hidden-depth must not exceed surface.depth");
            }
        }
    }

    public record AdaptiveDepth(boolean enabled, int maxDepth, int minHiddenDepth) {
        public AdaptiveDepth {
            if (maxDepth < 0 || minHiddenDepth < 0 || minHiddenDepth > maxDepth) {
                throw new IllegalArgumentException("Adaptive depths must satisfy 0 <= min-hidden-depth <= max-depth");
            }
        }
    }
}
