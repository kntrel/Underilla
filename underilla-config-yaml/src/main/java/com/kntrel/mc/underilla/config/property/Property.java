package com.kntrel.mc.underilla.config.property;

import java.util.function.Function;

public interface Property<T, P> {

    static <T, P> Property<T, P> from(String name, Class<T> objectType, Class<P> valueType, Function<T, P> extractor) {
        return new PropertyImpl<>(name, objectType, valueType, extractor);
    }


    String name();

    Class<T> objectType();

    Class<P> valueType();

    P get(T object);

}
