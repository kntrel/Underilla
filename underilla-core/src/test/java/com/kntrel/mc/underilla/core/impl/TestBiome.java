package com.kntrel.mc.underilla.core.impl;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.Objects;

/** In-memory biome implementation for generation tests. */
public record TestBiome(ID id) implements Biome {

    public TestBiome(String id) {
        this(ID.of(id));
    }

    public TestBiome {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public String toString() { return id.toString(); }
}
