package com.kntrel.mc.underilla.config.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Retains a Java type, including generic arguments. Use {@link #of(Class)} for a class or a
 * direct subclass such as {@code new TypeToken<Set<String>>() {}} for a parameterized type.
 * References compare by their represented type, regardless of the subclass used to capture it.
 */
public abstract class TypeToken<T> {

    private final Type type;

    /** Captures the type argument from a direct, parameterized subclass of TypeToken. */
    protected TypeToken() {
        Type superclass = getClass().getGenericSuperclass();
        if (!(superclass instanceof ParameterizedType parameterized)
                || parameterized.getRawType() != TypeToken.class) {
            throw new IllegalArgumentException("TypeToken requires a direct, parameterized subclass");
        }
        this.type = parameterized.getActualTypeArguments()[0];
    }

    private TypeToken(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> TypeToken<T> of(Class<T> type) {
        return new TypeToken<T>(type) {};
    }

    public final Type type() {
        return type;
    }

    /**
     * Whether values with the other declared type can be used as this type. Supports Java
     * subtyping, invariant generic arguments, wildcard bounds, and primitive boxing, without
     * numeric widening or inference of unresolved type variables.
     */
    public final boolean isAssignableFrom(TypeToken<?> other) {
        return Types.isAssignable(type, Objects.requireNonNull(other, "other").type);
    }

    @Override
    public final boolean equals(Object other) {
        return other instanceof TypeToken<?> token && type.equals(token.type);
    }

    @Override
    public final int hashCode() {
        return type.hashCode();
    }

    @Override
    public final String toString() {
        return type.getTypeName();
    }
}
