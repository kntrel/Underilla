package com.kntrel.mc.underilla.core.generation;

import java.util.Objects;

/** Composed patchers and generation policies for one configured strategy. */
public record PatchingPlan(Patcher terrainPatcher, Patcher liquidPatcher, Boundary boundary, boolean generateNoise) {

    public PatchingPlan {
        Objects.requireNonNull(terrainPatcher, "terrainPatcher");
        Objects.requireNonNull(liquidPatcher, "liquidPatcher");
        Objects.requireNonNull(boundary, "boundary");
    }
}
