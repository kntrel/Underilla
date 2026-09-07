package com.kntrel.mc.underilla.core.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** A Minecraft resource identifier in {@code namespace:value} form. */
public record ID(String namespace, String value) {

    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern VALUE = Pattern.compile("[a-z0-9/._-]+");

    public static ID of(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        int separator = identifier.indexOf(':');
        if (separator < 0) {
            return of(DEFAULT_NAMESPACE, identifier);
        }
        return of(identifier.substring(0, separator), identifier.substring(separator + 1));
    }

    public static ID of(String namespace, String value) {
        return new ID(namespace, value);
    }

    public ID {
        namespace = requirePart(namespace, "namespace", NAMESPACE);
        value = requirePart(value, "value", VALUE);
    }

    @Override
    public String toString() {
        return namespace + ':' + value;
    }

    private static String requirePart(String part, String name, Pattern format) {
        Objects.requireNonNull(part, name);
        if (!format.matcher(part).matches()) {
            throw new IllegalArgumentException("Invalid identifier " + name + ": " + part);
        }
        return part;
    }
}
