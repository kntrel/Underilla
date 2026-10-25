package com.kntrel.mc.underilla.config.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class OperationTest {

    @Test
    void classFactorySupportsComparisonAndDefaultInfixBinding() {
        Operation<Integer, Integer, Boolean> lessEqual = Operation.of(
                Integer.class, Integer.class, Boolean.class, Pattern.literal("<="), (a, b) -> a <= b);

        assertEquals(TypeToken.of(Integer.class), lessEqual.aType());
        assertEquals(TypeToken.of(Integer.class), lessEqual.bType());
        assertEquals(TypeToken.of(Boolean.class), lessEqual.type());
        assertEquals(0, lessEqual.precedence());
        assertEquals(2, lessEqual.pattern().match(Slice.of("<= 64")));
        assertTrue(lessEqual.apply(63, 64));
        assertTrue(lessEqual.apply(64, 64));
        assertFalse(lessEqual.apply(65, 64));
    }

    @Test
    void explicitBindingUsesLeftToRightSourceOrderForMembership() {
        TypeToken<Set<String>> setType = new TypeToken<Set<String>>() {};
        Pattern pattern = Pattern.keyword("in");
        Operation<String, Set<String>, Boolean> membership = Operation.of(
                TypeToken.of(String.class), setType, TypeToken.of(Boolean.class),
                pattern, 20,
                (value, set) -> set.contains(value));

        assertEquals(TypeToken.of(String.class), membership.aType());
        assertSame(setType, membership.bType());
        assertSame(pattern, membership.pattern());
        assertEquals(20, membership.precedence());
        assertTrue(membership.apply("minecraft:caves", Set.of("minecraft:caves")));
        assertFalse(membership.apply("minecraft:plains", Set.of("minecraft:caves")));
    }

    @Test
    void supportsNonBooleanResultsAndExplicitClassBinding() {
        Operation<Integer, Integer, Integer> subtraction = Operation.of(
                Integer.class, Integer.class, Integer.class, Pattern.literal("-"),
                30,
                (a, b) -> a - b);

        assertEquals(5, subtraction.apply(8, 3));
        assertEquals(TypeToken.of(Integer.class), subtraction.type());
        assertEquals(30, subtraction.precedence());
    }

    @Test
    void supportsTheTypeTokenFactoryWithDefaultBinding() {
        TypeToken<String> type = TypeToken.of(String.class);
        Operation<String, String, String> concatenate = Operation.of(
                type, type, type, Pattern.literal("+"), (a, b) -> a + b);

        assertSame(type, concatenate.aType());
        assertSame(type, concatenate.bType());
        assertSame(type, concatenate.type());
        assertEquals("ab", concatenate.apply("a", "b"));
    }

    @Test
    void constructionAndMatchingDoNotComputeAndApplyingDoesNotRematch() {
        AtomicInteger matches = new AtomicInteger();
        AtomicInteger computations = new AtomicInteger();
        Pattern pattern = src -> {
            matches.incrementAndGet();
            return 1;
        };
        Operation<Integer, Integer, Integer> addition = Operation.of(
                Integer.class, Integer.class, Integer.class, pattern, (a, b) -> {
                    computations.incrementAndGet();
                    return a + b;
                });

        assertEquals(0, matches.get());
        assertEquals(0, computations.get());
        assertEquals(1, addition.pattern().match(Slice.of("+")));
        assertEquals(0, computations.get());
        assertEquals(5, addition.apply(2, 3));
        assertEquals(1, matches.get());
        assertEquals(1, computations.get());
    }

    @Test
    void preservesComputationFailuresAndDelegatesOperandValidation() {
        IllegalArgumentException failure = new IllegalArgumentException("invalid operand");
        Operation<String, String, Boolean> failing = Operation.of(
                String.class, String.class, Boolean.class, Pattern.literal("="), (a, b) -> {
                    throw failure;
                });
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> failing.apply("a", "b")));

        Operation<String, String, Boolean> nullable = Operation.of(
                String.class, String.class, Boolean.class, Pattern.literal("="), java.util.Objects::equals);
        assertTrue(nullable.apply(null, null));
        assertFalse(nullable.apply(null, "b"));
    }

    @Test
    void rejectsNullTypesPatternsAndComputations() {
        TypeToken<Integer> type = TypeToken.of(Integer.class);
        Pattern pattern = Pattern.literal("+");
        BiFunction<Integer, Integer, Integer> compute = Integer::sum;

        assertThrows(NullPointerException.class, () -> Operation.of((Class<Integer>) null,
                Integer.class, Integer.class, pattern, compute));
        assertThrows(NullPointerException.class, () -> Operation.of(Integer.class,
                (Class<Integer>) null, Integer.class, pattern, compute));
        assertThrows(NullPointerException.class, () -> Operation.of(Integer.class,
                Integer.class, (Class<Integer>) null, pattern, compute));
        assertThrows(NullPointerException.class, () -> Operation.of((TypeToken<Integer>) null,
                type, type, pattern, compute));
        assertThrows(NullPointerException.class, () -> Operation.of(type,
                (TypeToken<Integer>) null, type, pattern, compute));
        assertThrows(NullPointerException.class, () -> Operation.of(type,
                type, (TypeToken<Integer>) null, pattern, compute));
        assertThrows(NullPointerException.class, () -> Operation.of(type, type, type, null, compute));
        assertThrows(NullPointerException.class, () -> Operation.of(type, type, type, pattern, null));
    }
}
