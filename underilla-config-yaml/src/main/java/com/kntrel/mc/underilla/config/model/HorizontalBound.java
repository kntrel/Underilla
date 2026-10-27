package com.kntrel.mc.underilla.config.model;

/** A horizontal block boundary, either chunk-aligned or inferred from source region files. */
public sealed interface HorizontalBound permits HorizontalBound.Fixed, HorizontalBound.Auto {
    static HorizontalBound fixed(int blocks) { return new Fixed(blocks); }

    static HorizontalBound auto() { return Auto.INSTANCE; }

    record Fixed(int blocks) implements HorizontalBound {
        public Fixed {
            if (Math.floorMod(blocks, 16) != 0) {
                throw new IllegalArgumentException("Horizontal area bounds must be multiples of 16");
            }
        }
    }

    enum Auto implements HorizontalBound { INSTANCE }
}
