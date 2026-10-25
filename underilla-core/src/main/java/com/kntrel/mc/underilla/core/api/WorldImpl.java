package com.kntrel.mc.underilla.core.api;

import java.util.Objects;

record WorldImpl(String name, ID dimension, int minHeight, int maxHeight) implements World {

    WorldImpl {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimension, "dimension");
        if (maxHeight <= minHeight) {
            throw new IllegalArgumentException("maxHeight must be greater than minHeight");
        }
    }
}
