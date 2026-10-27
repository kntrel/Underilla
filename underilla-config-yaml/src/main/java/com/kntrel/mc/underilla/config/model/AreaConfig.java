package com.kntrel.mc.underilla.config.model;

import java.util.Objects;

/** Minimum-inclusive, maximum-exclusive block bounds; auto X/Z values are resolved before generation. */
public record AreaConfig(HorizontalBound minX, HorizontalBound minZ, HorizontalBound maxX,
                         HorizontalBound maxZ, int minY, int maxY) {
    public AreaConfig {
        Objects.requireNonNull(minX, "min-x");
        Objects.requireNonNull(minZ, "min-z");
        Objects.requireNonNull(maxX, "max-x");
        Objects.requireNonNull(maxZ, "max-z");
        if (minX instanceof HorizontalBound.Fixed min && maxX instanceof HorizontalBound.Fixed max
                && max.blocks() <= min.blocks()) throw new IllegalArgumentException("max-x must exceed min-x");
        if (minZ instanceof HorizontalBound.Fixed min && maxZ instanceof HorizontalBound.Fixed max
                && max.blocks() <= min.blocks()) throw new IllegalArgumentException("max-z must exceed min-z");
        if (maxY <= minY) throw new IllegalArgumentException("max-y must exceed min-y");
    }

    public static AreaConfig defaults() {
        return new AreaConfig(HorizontalBound.fixed(0), HorizontalBound.fixed(0),
                HorizontalBound.fixed(512), HorizontalBound.fixed(512), -64, 320);
    }
}
