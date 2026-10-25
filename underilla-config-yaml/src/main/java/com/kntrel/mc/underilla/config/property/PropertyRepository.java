package com.kntrel.mc.underilla.config.property;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PropertyRepository {

    /**
     * Creates an immutable in-memory snapshot for concurrent lookups using exact class matching.
     * Returned lists preserve input order. Properties themselves are retained by reference.
     *
     * @throws NullPointerException if the collection, a property, or its metadata is null
     * @throws IllegalArgumentException if a name occurs more than once for the same object type
     */
    static PropertyRepository from(Collection<? extends Property<?, ?>> properties) {
        return new PropertyRepositoryImpl(properties);
    }


    <T> List<? extends Property<T, ?>> getAll(Class<T> type);

    <T> Optional<Property<T, ?>> get(Class<T> type, String name);

    <T, P> Optional<Property<T, P>> get(Class<T> type, String name, Class<P> valueType);

    <T, P> List<? extends Property<T, P>> get(Class<T> type, Class<P> valueType);
}
