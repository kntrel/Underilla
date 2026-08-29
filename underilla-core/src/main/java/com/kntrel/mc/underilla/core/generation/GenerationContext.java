package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.BlockFactory;
import java.util.Objects;

/** Dependencies required by platform-neutral generation code. */
public record GenerationContext(GenerationConfig config, BlockFactory blocks) {

    public GenerationContext {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(blocks, "blocks");
    }
}
