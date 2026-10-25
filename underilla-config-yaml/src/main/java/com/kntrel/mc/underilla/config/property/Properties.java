package com.kntrel.mc.underilla.config.property;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Discovers public property getters, including inherited and implemented declarations. */
public final class Properties {

    private static final Logger LOGGER = LoggerFactory.getLogger(Properties.class);

    private Properties() {}

    /**
     * Returns properties whose object type is {@code type} and whose value type is the getter's
     * declared return type. Invalid annotated getters are skipped with a warning.
     */
    public static <T> List<Property<T, ?>> of(Class<T> type) {
        Objects.requireNonNull(type, "type");
        List<Property<T, ?>> properties = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (method.isBridge() || method.isSynthetic()) { continue; }

            Prop annotation = method.getAnnotation(Prop.class);
            if (annotation == null) {
                annotation = annotationFor(type, method);
            }
            if (annotation == null) { continue; }

            if (method.getParameterCount() != 0) {
                LOGGER.warn("Skipping @Prop method {}: property getters must have no parameters", method);
                continue;
            }
            if (method.getReturnType() == void.class) {
                LOGGER.warn("Skipping @Prop method {}: property getters must not return void", method);
                continue;
            }
            properties.add(fromMethod(type, method.getReturnType(), method, annotation));
        }
        return List.copyOf(properties);
    }

    private static Prop annotationFor(Class<?> type, Method method) {
        try {
            Method declaration = type.getDeclaredMethod(method.getName(), method.getParameterTypes());
            if (Modifier.isPublic(declaration.getModifiers())) {
                Prop annotation = declaration.getAnnotation(Prop.class);
                if (annotation != null) {
                    return annotation;
                }
            }
        } catch (NoSuchMethodException _) { /* This type may inherit the getter rather than declare it.*/ }
        Class<?> superclass = type.getSuperclass();
        if (superclass != null) {
            Prop annotation = annotationFor(superclass, method);
            if (annotation != null) {
                return annotation;
            }
        }
        for (Class<?> implemented : type.getInterfaces()) {
            Prop annotation = annotationFor(implemented, method);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private static <T, P> Property<T, P> fromMethod(Class<T> objectType, Class<P> valueType, Method method, Prop annotation) {
        MethodHandle handle;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                handle = lookup.unreflect(method);
            } catch (IllegalAccessException inaccessible) {
                // Public getters can belong to non-public implementation classes.
                handle = MethodHandles.privateLookupIn(method.getDeclaringClass(), lookup).unreflect(method);
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Cannot access property getter " + method, exception);
        }
        if (Modifier.isStatic(method.getModifiers())) {
            handle = MethodHandles.dropArguments(handle, 0, Object.class);
        }
        handle = handle.asType(MethodType.methodType(Object.class, Object.class));
        String name = annotation.value();
        if (name.isEmpty()) {
            name = method.getName();
            if (name.startsWith("get") && name.length() > 3) {
                name = name.substring(3);
                name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            }
        }
        return Property.from(name, objectType, valueType, new PropertyMethod<>(handle, method));
    }

    private record PropertyMethod<T, P>(MethodHandle handle, Method method) implements Function<T, P> {

        @Override
            @SuppressWarnings("unchecked")
            public P apply(T object) {
                try {
                    return (P) this.handle.invokeExact((Object) object);
                } catch (RuntimeException | Error exception) {
                    throw exception;
                } catch (Throwable exception) {
                    throw new IllegalStateException("Failed to invoke property getter " + this.method, exception);
                }
            }
        }
}
