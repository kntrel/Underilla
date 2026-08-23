package com.kntrel.mc.underilla.core.impl;

import com.kntrel.mc.underilla.core.api.Biome;
import java.util.Objects;

/** In-memory biome implementation for generation tests. */
public record TestBiome(String name) implements Biome {

    public TestBiome {
        Objects.requireNonNull(name, "name");
    }

    @Override
    public String getName() { return name; }

    @Override
    public String toString() { return name; }
}
