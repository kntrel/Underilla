package com.kntrel.mc.underilla.core.generation;

/** Vanilla generation stages that should participate in Underilla's chunk composition. */
public record GenerationFlags(
        boolean noise,
        boolean surface,
        boolean carvers,
        boolean features,
        boolean mobs,
        boolean structures
) {}
