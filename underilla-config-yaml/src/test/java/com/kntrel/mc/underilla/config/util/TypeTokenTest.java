package com.kntrel.mc.underilla.config.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.kntrel.mc.underilla.config.util.TypeToken;
import org.junit.jupiter.api.Test;

class TypeTokenTest {

    @Test
    void classReferencesAndCapturedClassReferencesRepresentTheSameType() {
        TypeToken<String> explicit = TypeToken.of(String.class);
        TypeToken<String> captured = new TypeToken<String>() {};

        assertSame(String.class, explicit.type());
        assertSame(String.class, captured.type());
        assertEquals(explicit, captured);
        assertEquals(captured, explicit);
        assertEquals(explicit.hashCode(), captured.hashCode());
        assertEquals("java.lang.String", explicit.toString());
        assertSame(int.class, TypeToken.of(int.class).type());
    }

    @Test
    void retainsAndDistinguishesGenericArguments() {
        TypeToken<Set<String>> strings = new TypeToken<Set<String>>() {};
        TypeToken<Set<String>> same = new TypeToken<Set<String>>() {};
        TypeToken<Set<Integer>> integers = new TypeToken<Set<Integer>>() {};
        ParameterizedType type = (ParameterizedType) strings.type();

        assertSame(Set.class, type.getRawType());
        assertEquals(List.of(String.class), List.of(type.getActualTypeArguments()));
        assertEquals(strings, same);
        assertEquals(strings.hashCode(), same.hashCode());
        assertNotEquals(strings, integers);
        assertNotEquals(strings, TypeToken.of(Set.class));
        assertEquals("java.util.Set<java.lang.String>", strings.toString());
    }

    @Test
    void retainsNestedParameterizedTypes() {
        TypeToken<Map<String, List<Integer>>> reference = new TypeToken<Map<String, List<Integer>>>() {};
        ParameterizedType map = (ParameterizedType) reference.type();
        Type[] arguments = map.getActualTypeArguments();
        ParameterizedType list = (ParameterizedType) arguments[1];

        assertSame(Map.class, map.getRawType());
        assertSame(String.class, arguments[0]);
        assertSame(List.class, list.getRawType());
        assertEquals(List.of(Integer.class), List.of(list.getActualTypeArguments()));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void rejectsNullClassesAndMissingDirectTypeArguments() {
        assertThrows(NullPointerException.class, () -> TypeToken.of(null));
        assertThrows(IllegalArgumentException.class, () -> new TypeToken() {});
        assertThrows(IllegalArgumentException.class, () -> new StringTypeToken() {});
        assertSame(String.class, new StringTypeToken().type());
        assertFalse(TypeToken.of(String.class).equals(null));
        assertFalse(TypeToken.of(String.class).equals(String.class));
    }

    private static class StringTypeToken extends TypeToken<String> {}
}
