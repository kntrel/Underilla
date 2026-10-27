package com.kntrel.mc.underilla.config.model;

import java.util.Objects;

/**
 * Immutable settings corresponding to the bundled {@code config.yml}.
 * YAML kebab-case keys map to camelCase components. Paths remain relative until resolved
 * against the server root; UCEL selectors are executable predicates over their scoped contexts.
 */
public record UnderillaConfig(
        WorldsConfig worlds,
        AreaConfig area,
        MergeConfig merge,
        BiomesConfig biomes,
        GenerationConfig generation,
        CleanupConfig cleanup,
        PerformanceConfig performance,
        DiagnosticsConfig diagnostics
) {
    public UnderillaConfig {
        Objects.requireNonNull(worlds, "worlds");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(merge, "merge");
        Objects.requireNonNull(biomes, "biomes");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(cleanup, "cleanup");
        Objects.requireNonNull(performance, "performance");
        Objects.requireNonNull(diagnostics, "diagnostics");
    }

}
