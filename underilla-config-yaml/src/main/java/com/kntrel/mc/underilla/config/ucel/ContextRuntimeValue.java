package com.kntrel.mc.underilla.config.ucel;

import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.dsl.RuntimeValue;
import com.kntrel.mc.underilla.config.dsl.Slice;
import com.kntrel.mc.underilla.config.property.Property;
import com.kntrel.mc.underilla.config.property.PropertyRepository;
import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Matches a complete registered context property chain, including optional whitespace around dots.
 * Binding resolves declared property types and composes getters; evaluation alone reads the context.
 * The DSL treats the matched chain as a single runtime value and has no knowledge of its syntax.
 */
final class ContextRuntimeValue<C> implements RuntimeValue<C> {
    private final PropertyRepository registry;
    private final Map<String, Property<C, ?>> roots;

    ContextRuntimeValue(Class<C> contextType, PropertyRepository registry) {
        Objects.requireNonNull(contextType, "contextType");
        this.registry = Objects.requireNonNull(registry, "registry");
        var roots = new LinkedHashMap<String, Property<C, ?>>();
        for (Property<C, ?> property : registry.getAll(contextType)) {
            roots.put(property.name(), property);
        }
        this.roots = Map.copyOf(roots);
    }

    @Override
    public Pattern pattern() {
        return source -> {
            int end = PropertyNames.length(source, 0);
            if (end == 0 || !roots.containsKey(source.subSequence(0, end).toString())) return 0;
            while (true) {
                int dot = skipWhitespace(source, end);
                if (dot == source.length() || source.charAt(dot) != '.') return end;
                int start = skipWhitespace(source, dot + 1);
                int length = PropertyNames.length(source, start);
                if (length == 0) return end;
                end = start + length;
            }
        };
    }

    @Override
    public Bound<C, ?> bind(String matched) {
        Objects.requireNonNull(matched, "matched");
        Slice source = Slice.of(matched);
        if (matched.isEmpty() || pattern().match(source) != source.length()) {
            throw new IllegalArgumentException("Expected a complete context property chain");
        }
        int end = PropertyNames.length(source, 0);
        Property<?, ?> property = roots.get(source.subSequence(0, end).toString());
        var chain = new ArrayList<Property<?, ?>>();
        chain.add(property);
        while (end < source.length()) {
            int start = skipWhitespace(source, skipWhitespace(source, end) + 1);
            end = start + PropertyNames.length(source, start);
            String name = source.subSequence(start, end).toString();
            Class<?> owner = property.valueType();
            property = registry.get(owner, name).orElseThrow(
                    () -> new IllegalArgumentException("Unknown property '" + name + "' on " + owner.getTypeName()));
            chain.add(property);
        }
        return bound(property.valueType(), List.copyOf(chain));
    }

    private <T> Bound<C, T> bound(Class<T> resultType, List<Property<?, ?>> chain) {
        return Bound.of(TypeToken.of(resultType), context -> {
            Object value = context;
            for (Property<?, ?> property : chain) value = read(property, value);
            return castResult(value);
        });
    }

    // Every owner/result type was checked through the registry while binding the chain.
    @SuppressWarnings("unchecked")
    private static <A, T> T read(Property<A, T> property, Object owner) { return property.get((A) owner); }

    @SuppressWarnings("unchecked")
    private static <T> T castResult(Object value) { return (T) value; }

    private static int skipWhitespace(Slice source, int cursor) {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
        return cursor;
    }
}
