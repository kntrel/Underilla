package com.kntrel.mc.underilla.core.generation;

import java.util.List;

/** Selects positions contained by any of its component masks. */
public final class UnionWorldMask implements WorldMask {

    private final List<WorldMask> masks;

    public UnionWorldMask(WorldMask... masks) {
        this.masks = List.of(masks);
    }

    @Override
    public boolean contains(int globalX, int y, int globalZ) {
        for (WorldMask mask : masks) {
            if (mask.contains(globalX, y, globalZ)) {
                return true;
            }
        }
        return false;
    }
}
