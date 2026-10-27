package com.kntrel.mc.underilla.config.model;

import java.util.Objects;

public record PerformanceConfig(Cache cache) {
    public PerformanceConfig { Objects.requireNonNull(cache, "cache"); }

    public static PerformanceConfig defaults() { return new PerformanceConfig(new Cache(16, 64)); }

    public record Cache(int regionFiles, int chunks) {
        public Cache {
            if (regionFiles < 1 || chunks < 1) throw new IllegalArgumentException("Cache sizes must be at least 1");
        }
    }
}
