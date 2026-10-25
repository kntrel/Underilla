package com.kntrel.mc.underilla.config.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class NodeTest {

    private static final TypeToken<Integer> INTEGER = TypeToken.of(Integer.class);
    private static final TypeToken<Boolean> BOOLEAN = TypeToken.of(Boolean.class);

    @Test
    void literalsReturnTheirStoredValueIndependentlyOfContext() {
        Object value = new Object();
        Node<Context, Object> literal = new Node.Literal<>(TypeToken.of(Object.class), 0, 0, value);

        assertSame(value, literal.evaluate(new Context(1, Set.of())));
        assertSame(value, literal.evaluate(null));
        assertNull(new Node.Literal<Context, String>(TypeToken.of(String.class), 0, 0, null).evaluate(null));
    }

    @Test
    void runtimeValuesComputeOnEachEvaluationUsingTheSuppliedContext() {
        AtomicInteger computations = new AtomicInteger();
        Node<Context, Integer> runtime = new Node.Runtime<>(INTEGER, 0, 0, context -> {
            computations.incrementAndGet();
            return context.x();
        });

        assertEquals(0, computations.get());
        assertEquals(12, runtime.evaluate(new Context(12, Set.of())));
        assertEquals(24, runtime.evaluate(new Context(24, Set.of())));
        assertEquals(2, computations.get());
    }

    @Test
    void comparisonTreeCanBeExposedAsAReusableFunction() {
        Operation<Integer, Integer, Boolean> lessEqual = Operation.of(
                Integer.class, Integer.class, Boolean.class, Pattern.literal("<="), (a, b) -> a <= b);
        Node<Context, Boolean> root = new Node.Operation<>(BOOLEAN, 0, 0,
                new Node.Runtime<>(INTEGER, 0, 0, Context::x), new Node.Literal<>(INTEGER, 0, 0, 64), lessEqual);
        Function<Context, Boolean> expression = root::evaluate;

        assertTrue(expression.apply(new Context(63, Set.of())));
        assertTrue(expression.apply(new Context(64, Set.of())));
        assertFalse(expression.apply(new Context(65, Set.of())));
    }

    @Test
    void nestedTransformationsAndOperationsPropagateTheSameContext() {
        TypeToken<Set<String>> tagsType = new TypeToken<Set<String>>() {};
        Node<Context, Set<String>> tags = new Node.Runtime<>(tagsType, 0, 0, Context::tags);
        Node<Context, Integer> count = new Node.Transformation<>(INTEGER, 0, 0, tags, Set::size);
        Node<Context, Boolean> expression = new Node.Operation<>(BOOLEAN, 0, 0,
                count, new Node.Literal<>(INTEGER, 0, 0, 2), (a, b) -> a >= b);

        assertSame(tagsType, tags.type());
        assertSame(INTEGER, count.type());
        assertSame(BOOLEAN, expression.type());

        assertFalse(expression.evaluate(new Context(0, Set.of("caves"))));
        assertTrue(expression.evaluate(new Context(0, Set.of("caves", "overworld"))));
    }

    @Test
    void operationsEvaluateEachOperandOnceInLeftToRightOrder() {
        List<String> events = new ArrayList<>();
        Context context = new Context(10, Set.of());
        Node<Context, Integer> left = new Node.Runtime<>(INTEGER, 0, 0, current -> {
            assertSame(context, current);
            events.add("left");
            return current.x();
        });
        Node<Context, Integer> right = new Node.Runtime<>(INTEGER, 0, 0, current -> {
            assertSame(context, current);
            events.add("right");
            return 3;
        });
        Node<Context, Integer> operation = new Node.Operation<>(INTEGER, 0, 0, left, right, (a, b) -> {
            events.add("operation");
            return a - b;
        });

        assertEquals(7, operation.evaluate(context));
        assertEquals(List.of("left", "right", "operation"), events);
    }

    @Test
    void contextlessTreesCanBeExposedAsSuppliers() {
        AtomicInteger value = new AtomicInteger(1);
        Node<Void, Integer> root = new Node.Operation<>(INTEGER, 0, 0,
                new Node.Runtime<>(INTEGER, 0, 0, ignored -> value.get()), new Node.Literal<>(INTEGER, 0, 0, 2), Integer::sum);
        java.util.function.Supplier<Integer> expression = () -> root.evaluate(null);

        assertEquals(3, expression.get());
        value.set(8);
        assertEquals(10, expression.get());
    }

    @Test
    void evaluationFailuresPropagateAndStopRemainingEvaluation() {
        IllegalArgumentException failure = new IllegalArgumentException("failed computation");
        Node<Context, Integer> failing = new Node.Runtime<>(INTEGER, 0, 0, context -> {
            throw failure;
        });
        AtomicInteger rightEvaluations = new AtomicInteger();
        Node<Context, Integer> expression = new Node.Operation<>(INTEGER, 0, 0, failing,
                new Node.Runtime<>(INTEGER, 0, 0, context -> rightEvaluations.incrementAndGet()), Integer::sum);

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> expression.evaluate(new Context(0, Set.of()))));
        assertEquals(0, rightEvaluations.get());
    }

    private record Context(int x, Set<String> tags) {}
}
