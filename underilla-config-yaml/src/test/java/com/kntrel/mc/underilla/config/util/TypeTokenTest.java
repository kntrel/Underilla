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
    void infersBoxedCommonParentsAndConstructsParameterizedLists() {
        assertEquals(TypeToken.of(Number.class), TypeToken.commonParent(List.of(
                TypeToken.of(int.class), TypeToken.of(double.class), TypeToken.of(float.class))));
        assertEquals(TypeToken.of(Object.class), TypeToken.commonParent(List.of(
                TypeToken.of(Integer.class), TypeToken.of(String.class))));
        assertEquals(new TypeToken<List<Integer>>() {}, TypeToken.of(Integer.class).listType());
        assertEquals("java.util.List<java.lang.Integer>", TypeToken.of(Integer.class).listType().toString());
        TypeToken<?> empty = TypeToken.commonParent(List.of()).listType();
        assertEquals(new TypeToken<List<String>>() {}, empty.specializeList(new TypeToken<List<String>>() {}));
        assertFalse(new TypeToken<List<String>>() {}.isAssignableFrom(empty));
        assertFalse(TypeToken.of(String.class).isAssignableFrom(TypeToken.of(Void.class)));
        assertFalse(new TypeToken<List<String>>() {}.isAssignableFrom(new TypeToken<List<Void>>() {}));
    }

    @Test
    void preservesGenericArgumentsAndDoesNotDependOnElementOrder() {
        TypeToken<?> integers = new TypeToken<List<Integer>>() {};
        TypeToken<?> doubles = new TypeToken<List<Double>>() {};
        TypeToken<?> parent = new TypeToken<List<? extends Number>>() {};
        assertEquals(parent, TypeToken.commonParent(List.of(integers, doubles)));
        assertEquals(parent, TypeToken.commonParent(List.of(doubles, integers)));
        assertEquals(integers, TypeToken.commonParent(List.of(integers, integers)));
        assertEquals(TypeToken.of(Object.class), TypeToken.commonParent(List.of(
                new TypeToken<java.util.ArrayList<Integer>>() {}, integers)));
        assertEquals(TypeToken.of(Object.class), TypeToken.commonParent(List.of(
                integers, new TypeToken<java.util.ArrayList<Integer>>() {})));
    }

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
