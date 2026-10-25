package com.kntrel.mc.underilla.config.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.kntrel.mc.underilla.config.util.TypeToken;
import org.junit.jupiter.api.Test;

class TypeTokenAssignabilityTest {

    @Test
    void supportsClassSubtypingAndBoxingWithoutNumericPromotion() {
        assertTrue(TypeToken.of(Number.class).isAssignableFrom(TypeToken.of(Integer.class)));
        assertTrue(TypeToken.of(Number.class).isAssignableFrom(TypeToken.of(double.class)));
        assertTrue(TypeToken.of(Integer.class).isAssignableFrom(TypeToken.of(int.class)));
        assertTrue(TypeToken.of(int.class).isAssignableFrom(TypeToken.of(Integer.class)));
        assertFalse(TypeToken.of(Integer.class).isAssignableFrom(TypeToken.of(Number.class)));
        assertFalse(TypeToken.of(Double.class).isAssignableFrom(TypeToken.of(Integer.class)));
        assertTrue(TypeToken.of(CharSequence.class).isAssignableFrom(TypeToken.of(String.class)));
        assertThrows(NullPointerException.class, () -> TypeToken.of(Object.class).isAssignableFrom(null));
    }

    @Test
    void preservesGenericInvarianceAcrossSupertypes() {
        assertTrue(new TypeToken<Collection<Integer>>() {}.isAssignableFrom(new TypeToken<Set<Integer>>() {}));
        assertFalse(new TypeToken<Collection<Number>>() {}.isAssignableFrom(new TypeToken<Set<Integer>>() {}));
        assertTrue(new TypeToken<Iterable<? extends Number>>() {}.isAssignableFrom(new TypeToken<Set<Integer>>() {}));
        assertFalse(new TypeToken<Iterable<? extends Number>>() {}.isAssignableFrom(new TypeToken<Set<String>>() {}));
        assertTrue(new TypeToken<Comparable<Integer>>() {}.isAssignableFrom(TypeToken.of(Integer.class)));
        assertFalse(new TypeToken<Comparable<Number>>() {}.isAssignableFrom(TypeToken.of(Integer.class)));
    }

    @Test
    void handlesUpperAndLowerWildcardBounds() {
        assertTrue(new TypeToken<List<? extends Number>>() {}.isAssignableFrom(new TypeToken<List<? extends Integer>>() {}));
        assertTrue(new TypeToken<List<? super Integer>>() {}.isAssignableFrom(new TypeToken<List<Number>>() {}));
        assertTrue(new TypeToken<List<? super Integer>>() {}.isAssignableFrom(new TypeToken<List<? super Number>>() {}));
        assertFalse(new TypeToken<List<? super Number>>() {}.isAssignableFrom(new TypeToken<List<Integer>>() {}));
        assertFalse(new TypeToken<List<? super Integer>>() {}.isAssignableFrom(new TypeToken<List<? extends Number>>() {}));
        assertFalse(new TypeToken<List<? extends Number>>() {}.isAssignableFrom(new TypeToken<List<? super Integer>>() {}));
    }

    @Test
    void rawSourcesCannotPretendToHaveKnownGenericArguments() {
        assertTrue(new TypeToken<List<?>>() {}.isAssignableFrom(TypeToken.of(List.class)));
        assertFalse(new TypeToken<List<String>>() {}.isAssignableFrom(TypeToken.of(List.class)));
        assertFalse(new TypeToken<List<? extends Number>>() {}.isAssignableFrom(TypeToken.of(List.class)));
    }

    @Test
    void resolvesTypeArgumentsThroughSeveralInheritanceLevels() {
        assertTrue(new TypeToken<Carrier<List<Integer>>>() {}.isAssignableFrom(TypeToken.of(Integers.class)));
        assertFalse(new TypeToken<Carrier<List<Number>>>() {}.isAssignableFrom(TypeToken.of(Integers.class)));
        assertTrue(new TypeToken<Carrier<? extends List<? extends Number>>>() {}
                .isAssignableFrom(TypeToken.of(Integers.class)));
    }

    @Test
    void resolvesGenericOwnerBindings() {
        assertTrue(new TypeToken<Carrier<String>>() {}.isAssignableFrom(new TypeToken<Outer<String>.Inner>() {}));
        assertFalse(new TypeToken<Carrier<Integer>>() {}.isAssignableFrom(new TypeToken<Outer<String>.Inner>() {}));
        assertTrue(new TypeToken<Outer<?>.Inner>() {}.isAssignableFrom(new TypeToken<Outer<String>.Inner>() {}));
        assertFalse(new TypeToken<Outer<Integer>.Inner>() {}.isAssignableFrom(new TypeToken<Outer<String>.Inner>() {}));
    }

    @Test
    void handlesArrayCovarianceWithoutBoxingPrimitiveArrayElements() {
        assertTrue(TypeToken.of(Number[].class).isAssignableFrom(TypeToken.of(Integer[].class)));
        assertFalse(TypeToken.of(Number[].class).isAssignableFrom(TypeToken.of(int[].class)));
        assertTrue(TypeToken.of(Object.class).isAssignableFrom(TypeToken.of(int[].class)));
        assertTrue(TypeToken.of(Object[].class).isAssignableFrom(TypeToken.of(int[][].class)));
        assertTrue(new TypeToken<List<? extends Number>[]>() {}.isAssignableFrom(new TypeToken<List<Integer>[]>() {}));
        assertFalse(new TypeToken<List<Number>[]>() {}.isAssignableFrom(new TypeToken<List<Integer>[]>() {}));
    }

    @Test
    void usesSourceTypeVariableBoundsWithoutInferringTargetVariables() {
        assertTrue(TypeToken.of(Number.class).isAssignableFrom(numberVariable()));
        assertFalse(TypeToken.of(Integer.class).isAssignableFrom(numberVariable()));
        assertFalse(numberVariable().isAssignableFrom(TypeToken.of(Integer.class)));
    }

    private static <T extends Number> TypeToken<T> numberVariable() { return new TypeToken<T>() {}; }

    private interface Carrier<T> {}
    private static class Base<T> implements Carrier<List<T>> {}
    private static class Integers extends Base<Integer> {}
    private static class Outer<T> {
        class Inner implements Carrier<T> {}
    }
}
