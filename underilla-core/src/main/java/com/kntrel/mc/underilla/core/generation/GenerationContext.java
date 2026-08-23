package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.GenerationLogger;
import java.util.Objects;

/** Dependencies required by platform-neutral generation code. */
public record GenerationContext(GenerationConfig config, BlockFactory blocks, GenerationLogger logger) {

    public GenerationContext {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(logger, "logger");
    }
}
