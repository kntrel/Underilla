package com.kntrel.mc.underilla.config.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class RuntimeValueTest {

    @Test
    void classFactoryReadsTheContextSuppliedForEachEvaluation() {
        Pattern pattern = Pattern.literal("ref.block.x");
        RuntimeValue<Context, Integer> value = RuntimeValue.of(Integer.class, pattern, Context::x);

        assertEquals(TypeToken.of(Integer.class), value.type());
        assertSame(pattern, value.pattern());
        assertEquals(12, value.compute(new Context(12, Set.of())));
        assertEquals(24, value.compute(new Context(24, Set.of())));
    }

    @Test
    void typeTokenFactoryRetainsParameterizedResultMetadata() {
        TypeToken<Set<String>> type = new TypeToken<Set<String>>() {};
        RuntimeValue<Context, Set<String>> value = RuntimeValue.of(
                type, Pattern.literal("ref.biomes.tags"), Context::tags);
        Set<String> tags = Set.of("minecraft:caves");

        assertSame(type, value.type());
        assertSame(tags, value.compute(new Context(0, tags)));
    }

    @Test
    void constructionAndMatchingDoNotComputeAndComputingDoesNotRematchOrCache() {
        AtomicInteger matches = new AtomicInteger();
        AtomicInteger computations = new AtomicInteger();
        Pattern pattern = src -> {
            matches.incrementAndGet();
            return 1;
        };
        RuntimeValue<Context, Integer> value = RuntimeValue.of(Integer.class, pattern, context -> {
            computations.incrementAndGet();
            return context.x();
        });

        assertEquals(0, matches.get());
        assertEquals(0, computations.get());
        assertEquals(1, value.pattern().match(Slice.of("x")));
        assertEquals(0, computations.get());
        assertEquals(12, value.compute(new Context(12, Set.of())));
        assertEquals(24, value.compute(new Context(24, Set.of())));
        assertEquals(1, matches.get());
        assertEquals(2, computations.get());
    }

    @Test
    void canBeAttachedToARuntimeNodeAndCombinedWithAnOperation() {
        RuntimeValue<Context, Integer> value = RuntimeValue.of(
                Integer.class, Pattern.literal("ref.block.x"), Context::x);
        Operation<Integer, Integer, Boolean> lessEqual = Operation.of(
                Integer.class, Integer.class, Boolean.class, Pattern.literal("<="), (a, b) -> a <= b);
        Node<Context, Boolean> root = new Node.Operation<>(lessEqual.type(), 0, 0,
                new Node.Runtime<>(value.type(), 0, 0, value::compute),
                new Node.Literal<>(TypeToken.of(Integer.class), 0, 0, 64), lessEqual);
        Function<Context, Boolean> expression = root::evaluate;

        assertTrue(expression.apply(new Context(64, Set.of())));
        assertFalse(expression.apply(new Context(65, Set.of())));
    }

    @Test
    void contextlessDefinitionsCanIgnoreNullContexts() {
        AtomicInteger current = new AtomicInteger(1);
        RuntimeValue<Void, Integer> value = RuntimeValue.of(
                Integer.class, Pattern.literal("current"), ignored -> current.get());

        assertEquals(1, value.compute(null));
        current.set(2);
        assertEquals(2, value.compute(null));
    }

    @Test
    void computationFailuresPropagateWithoutWrapping() {
        IllegalArgumentException failure = new IllegalArgumentException("missing current block");
        RuntimeValue<Context, Integer> value = RuntimeValue.of(
                Integer.class, Pattern.literal("ref.block.x"), context -> {
                    throw failure;
                });

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> value.compute(new Context(0, Set.of()))));
    }

    @Test
    void rejectsNullDefinitions() {
        Pattern pattern = Pattern.literal("ref.block.x");
        Function<Context, Integer> compute = Context::x;

        assertThrows(NullPointerException.class, () -> RuntimeValue.of((Class<Integer>) null, pattern, compute));
        assertThrows(NullPointerException.class, () -> RuntimeValue.of((TypeToken<Integer>) null, pattern, compute));
        assertThrows(NullPointerException.class, () -> RuntimeValue.of(Integer.class, null, compute));
        assertThrows(NullPointerException.class, () -> RuntimeValue.of(Integer.class, pattern, null));
    }

    private record Context(int x, Set<String> tags) {}
}
