package com.kntrel.mc.underilla.config.model;

import com.kntrel.mc.underilla.config.context.BiomeContext;
import com.kntrel.mc.underilla.config.context.StructureContext;
import java.util.Objects;

public record GenerationConfig(Population population, Structures structures, Carvers carvers) {
    public GenerationConfig {
        Objects.requireNonNull(population, "population");
        Objects.requireNonNull(structures, "structures");
        Objects.requireNonNull(carvers, "carvers");
    }

    public static GenerationConfig defaults() {
        return new GenerationConfig(new Population(true), new Structures(true, Selector.all()),
                new Carvers(true, true, Selector.all()));
    }

    public record Population(boolean enabled) {}

    /** Candidate structure and target metadata; spatial/biome values depend on adapter availability. */
    public record Structures(boolean enabled, Selector<StructureContext> allowIf) {
        public Structures { Objects.requireNonNull(allowIf, "structures.allow-if"); }
    }

    /** Selection uses source biome/world data only. */
    public record Carvers(boolean enabled, boolean preserveLiquids, Selector<BiomeContext> allowIf) {
        public Carvers { Objects.requireNonNull(allowIf, "carvers.allow-if"); }
    }
}
