package com.kntrel.mc.underilla.core.generation;

import java.util.List;
import java.util.Objects;

/** Composed patchers and generation policies for one configured strategy. */
public record PatchingPlan(List<Patcher> terrainPatchers, Patcher liquidPatcher, Boundary boundary, boolean generateNoise) {

    public PatchingPlan {
        terrainPatchers = List.copyOf(Objects.requireNonNull(terrainPatchers, "terrainPatchers"));
        if (terrainPatchers.isEmpty()) {
            throw new IllegalArgumentException("terrainPatchers must not be empty");
        }
        Objects.requireNonNull(liquidPatcher, "liquidPatcher");
        Objects.requireNonNull(boundary, "boundary");
    }

    public PatchingPlan(Patcher terrainPatcher, Patcher liquidPatcher, Boundary boundary, boolean generateNoise) {
        this(List.of(Objects.requireNonNull(terrainPatcher, "terrainPatcher")),
                liquidPatcher, boundary, generateNoise);
    }
}
