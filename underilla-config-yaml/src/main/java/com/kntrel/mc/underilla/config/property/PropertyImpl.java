package com.kntrel.mc.underilla.config.property;

import java.util.function.Function;

record PropertyImpl<T, P>(
        String name,
        Class<T> objectType,
        Class<P> valueType,
        Function<T, P> compute
) implements Property<T, P> {

    @Override
    public P get(T object) {
        return this.compute.apply(object);
    }
}
