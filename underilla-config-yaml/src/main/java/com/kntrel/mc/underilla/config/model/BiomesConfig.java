package com.kntrel.mc.underilla.config.model;

import com.kntrel.mc.underilla.config.context.DualBiomeContext;
import java.util.Objects;

public record BiomesConfig(Source source, Target target) {
    public BiomesConfig {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }

    public record Source(boolean topOnly) {}

    /** Bare biome/world roots still refer to source values inside this target section. */
    public record Target(Selector<DualBiomeContext> preserveIf, boolean outsideSourceTerrain) {
        public Target { Objects.requireNonNull(preserveIf, "preserve-if"); }
    }
}
