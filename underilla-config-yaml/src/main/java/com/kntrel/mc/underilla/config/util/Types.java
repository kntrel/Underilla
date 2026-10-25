package com.kntrel.mc.underilla.config.util;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Declared-type assignability without unchecked generic conversions. */
final class Types {

    private static final Map<Class<?>, Class<?>> BOXED = Map.of(
            boolean.class, Boolean.class, byte.class, Byte.class, short.class, Short.class,
            int.class, Integer.class, long.class, Long.class, float.class, Float.class,
            double.class, Double.class, char.class, Character.class, void.class, Void.class);

    private Types() {}

    static boolean isAssignable(Type target, Type source) {
        if (target.equals(source)) {
            return true;
        }
        if (target instanceof WildcardType wildcard) {
            return contains(wildcard, source);
        }
        if (source instanceof TypeVariable<?> variable) {
            return Arrays.stream(variable.getBounds()).anyMatch(bound -> isAssignable(target, bound));
        }
        if (source instanceof WildcardType wildcard) {
            return Arrays.stream(wildcard.getUpperBounds()).anyMatch(bound -> isAssignable(target, bound));
        }
        Type targetComponent = component(target);
        Type sourceComponent = component(source);
        if (targetComponent != null && sourceComponent != null) {
            if (isPrimitive(targetComponent) || isPrimitive(sourceComponent)) {
                return targetComponent.equals(sourceComponent);
            }
            return isAssignable(targetComponent, sourceComponent);
        }
        if (target instanceof Class<?> targetClass) {
            if (sourceComponent != null) {
                return targetClass == Object.class || targetClass == Cloneable.class
                        || targetClass == java.io.Serializable.class;
            }
            Class<?> sourceClass = rawClass(source);
            return sourceClass != null && boxed(targetClass).isAssignableFrom(boxed(sourceClass));
        }
        if (target instanceof ParameterizedType parameterized) {
            Type view = asSupertype(source, (Class<?>) parameterized.getRawType());
            if (view == null) {
                return false;
            }
            if (!(view instanceof ParameterizedType sourceType)) {
                return Arrays.stream(parameterized.getActualTypeArguments()).allMatch(Types::unbounded)
                        && !(parameterized.getOwnerType() instanceof ParameterizedType);
            }
            if (parameterized.getOwnerType() != null
                    && !isAssignable(parameterized.getOwnerType(), sourceType.getOwnerType())) {
                return false;
            }
            Type[] targets = parameterized.getActualTypeArguments();
            Type[] sources = sourceType.getActualTypeArguments();
            for (int i = 0; i < targets.length; i++) {
                if (!targets[i].equals(sources[i])
                        && !(targets[i] instanceof WildcardType wildcard && contains(wildcard, sources[i]))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean contains(WildcardType target, Type source) {
        for (Type upper : target.getUpperBounds()) {
            if (!isAssignable(upper, source)) {
                return false;
            }
        }
        for (Type lower : target.getLowerBounds()) {
            if (source instanceof WildcardType wildcard) {
                if (Arrays.stream(wildcard.getLowerBounds()).noneMatch(bound -> isAssignable(bound, lower))) {
                    return false;
                }
            } else if (!isAssignable(source, lower)) {
                return false;
            }
        }
        return true;
    }

    private static boolean unbounded(Type type) {
        return type instanceof WildcardType wildcard && wildcard.getLowerBounds().length == 0
                && Arrays.equals(wildcard.getUpperBounds(), new Type[] {Object.class});
    }

    private static Type asSupertype(Type source, Class<?> target) {
        Class<?> raw = rawClass(source);
        if (raw == null || !target.isAssignableFrom(raw)) {
            return null;
        }
        if (raw == target) {
            return source;
        }
        Map<TypeVariable<?>, Type> bindings = new HashMap<>();
        collectBindings(source, bindings);
        for (Type parent : raw.getGenericInterfaces()) {
            Type match = asSupertype(resolve(parent, bindings), target);
            if (match != null) {
                return match;
            }
        }
        Type parent = raw.getGenericSuperclass();
        return parent == null ? null : asSupertype(resolve(parent, bindings), target);
    }

    private static void collectBindings(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof ParameterizedType parameterized) {
            collectBindings(parameterized.getOwnerType(), bindings);
            TypeVariable<?>[] variables = ((Class<?>) parameterized.getRawType()).getTypeParameters();
            Type[] arguments = parameterized.getActualTypeArguments();
            for (int i = 0; i < variables.length; i++) {
                bindings.put(variables[i], arguments[i]);
            }
        }
    }

    private static Type resolve(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> variable) {
            return bindings.getOrDefault(variable, variable);
        }
        if (type instanceof ParameterizedType parameterized) {
            return new Parameterized((Class<?>) parameterized.getRawType(),
                    resolve(parameterized.getOwnerType(), bindings),
                    Arrays.stream(parameterized.getActualTypeArguments()).map(t -> resolve(t, bindings)).toList());
        }
        if (type instanceof GenericArrayType array) {
            return new GenericArray(resolve(array.getGenericComponentType(), bindings));
        }
        if (type instanceof WildcardType wildcard) {
            return new Wildcard(
                    Arrays.stream(wildcard.getUpperBounds()).map(t -> resolve(t, bindings)).toList(),
                    Arrays.stream(wildcard.getLowerBounds()).map(t -> resolve(t, bindings)).toList());
        }
        return type;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        return type instanceof ParameterizedType parameterized ? (Class<?>) parameterized.getRawType() : null;
    }

    private static Type component(Type type) {
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return clazz.getComponentType();
        }
        return type instanceof GenericArrayType array ? array.getGenericComponentType() : null;
    }

    private static boolean isPrimitive(Type type) {
        return type instanceof Class<?> clazz && clazz.isPrimitive();
    }

    private static Class<?> boxed(Class<?> type) {
        return BOXED.getOrDefault(type, type);
    }

    private record Parameterized(Class<?> raw, Type owner, List<Type> arguments) implements ParameterizedType {
        @Override public Type[] getActualTypeArguments() { return arguments.toArray(Type[]::new); }
        @Override public Type getRawType() { return raw; }
        @Override public Type getOwnerType() { return owner; }
        @Override public boolean equals(Object other) {
            return other instanceof ParameterizedType type && raw.equals(type.getRawType())
                    && Objects.equals(owner, type.getOwnerType())
                    && Arrays.equals(getActualTypeArguments(), type.getActualTypeArguments());
        }
        @Override public int hashCode() { return raw.hashCode() ^ Objects.hashCode(owner) ^ arguments.hashCode(); }
    }

    private record GenericArray(Type component) implements GenericArrayType {
        @Override public Type getGenericComponentType() { return component; }
        @Override public boolean equals(Object other) {
            return other instanceof GenericArrayType type && component.equals(type.getGenericComponentType());
        }
        @Override public int hashCode() { return component.hashCode(); }
    }

    private record Wildcard(List<Type> upper, List<Type> lower) implements WildcardType {
        @Override public Type[] getUpperBounds() { return upper.toArray(Type[]::new); }
        @Override public Type[] getLowerBounds() { return lower.toArray(Type[]::new); }
        @Override public boolean equals(Object other) {
            return other instanceof WildcardType type && Arrays.equals(getUpperBounds(), type.getUpperBounds())
                    && Arrays.equals(getLowerBounds(), type.getLowerBounds());
        }
        @Override public int hashCode() { return upper.hashCode() ^ lower.hashCode(); }
    }
}
