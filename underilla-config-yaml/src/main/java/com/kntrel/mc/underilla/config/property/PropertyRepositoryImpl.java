package com.kntrel.mc.underilla.config.property;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable in-memory index with exact object- and value-type matching.
 * Concurrent lookups are safe; property computation retains the supplied property's semantics.
 */
final class PropertyRepositoryImpl implements PropertyRepository {

    //FIELDS
    private final Map<Class<?>, Map<String, Property<?, ?>>> properties_;


    //CONSTRUCTOR
    PropertyRepositoryImpl(Collection<? extends Property<?, ?>> properties) {
        Objects.requireNonNull(properties, "properties");
        Map<Class<?>, Map<String, Property<?, ?>>> byType = new HashMap<>();
        for (Property<?, ?> property : properties) {
            Objects.requireNonNull(property, "property");
            Class<?> objectType = Objects.requireNonNull(property.objectType(), "property.objectType");
            String name = Objects.requireNonNull(property.name(), "property.name");
            Objects.requireNonNull(property.valueType(), "property.valueType");
            Map<String, Property<?, ?>> byName = byType.computeIfAbsent(
                    objectType, _ -> new LinkedHashMap<>());
            if (byName.putIfAbsent(name, property) != null) {
                throw new IllegalArgumentException(
                        "Duplicate property '" + name + "' for " + objectType.getTypeName());
            }
        }
        byType.replaceAll((_, byName) -> Collections.unmodifiableMap(byName));
        this.properties_ = Map.copyOf(byType);
    }

    @Override
    public <T> List<? extends Property<T, ?>> getAll(Class<T> type) {
        return propertiesFor(type).values().stream()
                .map(property -> PropertyRepositoryImpl.<T>castProperty(property))
                .toList();
    }

    @Override
    public <T> Optional<Property<T, ?>> get(Class<T> type, String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(propertiesFor(type).get(name))
                .map(property -> PropertyRepositoryImpl.<T>castProperty(property));
    }

    @Override
    public <T, P> Optional<Property<T, P>> get(Class<T> type, String name, Class<P> valueType) {
        Objects.requireNonNull(valueType, "valueType");
        return get(type, name)
                .filter(property -> property.valueType().equals(valueType))
                .map(property -> PropertyRepositoryImpl.<T, P>castValueProperty(property));
    }

    @Override
    public <T, P> List<? extends Property<T, P>> get(Class<T> type, Class<P> valueType) {
        Objects.requireNonNull(valueType, "valueType");
        return getAll(type).stream()
                .filter(property -> property.valueType().equals(valueType))
                .map(property -> PropertyRepositoryImpl.<T, P>castValueProperty(property))
                .toList();
    }

    private Map<String, Property<?, ?>> propertiesFor(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return this.properties_.getOrDefault(type, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static <T> Property<T, ?> castProperty(Property<?, ?> property) {
        // The index selects only properties registered with the requested object type.
        return (Property<T, ?>) property;
    }

    @SuppressWarnings("unchecked")
    private static <T, P> Property<T, P> castValueProperty(Property<T, ?> property) {
        // Callers have checked the property's value type against the requested class.
        return (Property<T, P>) property;
    }
}
