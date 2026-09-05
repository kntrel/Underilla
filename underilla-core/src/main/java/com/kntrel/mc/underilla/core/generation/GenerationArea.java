package com.kntrel.mc.underilla.core.generation;

/** Horizontal, maximum-exclusive area in which generation may be patched. */
public record GenerationArea(int minimumX, int minimumZ, int maximumX, int maximumZ) {

    public GenerationArea {
        if (maximumX < minimumX) {
            throw new IllegalArgumentException("maximumX must be greater than or equal to minimumX");
        }
        if (maximumZ < minimumZ) {
            throw new IllegalArgumentException("maximumZ must be greater than or equal to minimumZ");
        }
    }

    public static GenerationArea everywhere() {
        return new GenerationArea(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public boolean contains(int x, int z) {
        return x >= minimumX && x < maximumX && z >= minimumZ && z < maximumZ;
    }
}
