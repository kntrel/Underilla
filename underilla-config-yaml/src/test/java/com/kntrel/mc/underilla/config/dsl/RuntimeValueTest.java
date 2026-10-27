package com.kntrel.mc.underilla.config.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeValueTest {
    @Test
    void classFactoryReadsTheContextSuppliedForEachEvaluation() {
        Pattern pattern = Pattern.literal("x");
        RuntimeValue<Context> value = RuntimeValue.of(Integer.class, pattern, Context::x);
        var bound = value.bind("x");
        assertEquals(TypeToken.of(Integer.class), bound.type());
        assertSame(pattern, value.pattern());
        assertEquals(12, bound.compute(new Context(12, Set.of())));
        assertEquals(24, bound.compute(new Context(24, Set.of())));
    }

    @Test
    void typeTokenFactoryRetainsParameterizedResultMetadata() {
        TypeToken<Set<String>> type = new TypeToken<>() {};
        RuntimeValue<Context> value = RuntimeValue.of(type, Pattern.literal("tags"), Context::tags);
        Set<String> tags = Set.of("minecraft:caves");
        var bound = value.bind("tags");
        assertSame(type, bound.type());
        assertSame(tags, bound.compute(new Context(0, tags)));
    }

    @Test
    void constructionMatchingAndBindingDoNotComputeAndEvaluationDoesNotRematch() {
        AtomicInteger matches = new AtomicInteger();
        AtomicInteger computations = new AtomicInteger();
        Pattern pattern = source -> { matches.incrementAndGet(); return 1; };
        RuntimeValue<Context> value = RuntimeValue.of(Integer.class, pattern, context -> {
            computations.incrementAndGet();
            return context.x();
        });
        assertEquals(0, matches.get());
        assertEquals(1, value.pattern().match(Slice.of("x")));
        var bound = value.bind("x");
        assertEquals(0, computations.get());
        assertEquals(12, bound.compute(new Context(12, Set.of())));
        assertEquals(24, bound.compute(new Context(24, Set.of())));
        assertEquals(1, matches.get());
        assertEquals(2, computations.get());
    }

    @Test
    void oneDefinitionCanBindDifferentResultTypesDuringCompilation() {
        RuntimeValue<Context> value = new RuntimeValue<>() {
            @Override public Pattern pattern() { return Pattern.regex("number|flag"); }
            @Override public Bound<Context, ?> bind(String matched) {
                return switch (matched) {
                    case "number" -> Bound.of(TypeToken.of(Integer.class), Context::x);
                    case "flag" -> Bound.of(TypeToken.of(Boolean.class), context -> context.x() > 0);
                    default -> throw new IllegalArgumentException(matched);
                };
            }
        };
        var compiler = Compiler.of(Grammar.with(Context.class).standard().runtime(value).done());
        assertEquals(4, compiler.compile("number+1", Integer.class).apply(new Context(3, Set.of())));
        var predicate = compiler.compile("flag and number > 1", Boolean.class);
        assertTrue(predicate.apply(new Context(2, Set.of())));
        assertFalse(predicate.apply(new Context(-1, Set.of())));
        assertThrows(CompilationException.class, () -> compiler.compile("flag+1", Integer.class));
    }

    @Test
    void contextlessDefinitionsCanIgnoreNullContexts() {
        AtomicInteger current = new AtomicInteger(1);
        RuntimeValue<Void> value = RuntimeValue.of(Integer.class, Pattern.literal("current"), _ -> current.get());
        var bound = value.bind("current");
        assertEquals(1, bound.compute(null));
        current.set(2);
        assertEquals(2, bound.compute(null));
    }

    @Test
    void computationFailuresPropagateWithoutWrapping() {
        var failure = new IllegalArgumentException("missing current block");
        RuntimeValue<Context> value = RuntimeValue.of(Integer.class, Pattern.literal("x"), _ -> { throw failure; });
        var bound = value.bind("x");
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> bound.compute(new Context(0, Set.of()))));
    }

    @Test
    void rejectsNullDefinitionAndBindingComponents() {
        assertThrows(NullPointerException.class, () -> RuntimeValue.of((Class<Integer>) null, Pattern.literal("x"), x -> 1));
        assertThrows(NullPointerException.class, () -> RuntimeValue.of(Integer.class, null, x -> 1));
        assertThrows(NullPointerException.class, () -> RuntimeValue.of(Integer.class, Pattern.literal("x"), null));
        assertThrows(NullPointerException.class, () -> RuntimeValue.Bound.of(null, x -> 1));
        assertThrows(NullPointerException.class, () -> RuntimeValue.Bound.of(TypeToken.of(Integer.class), null));
        assertThrows(NullPointerException.class, () -> RuntimeValue.of(Integer.class, Pattern.literal("x"), x -> 1).bind(null));
    }

    private record Context(int x, Set<String> tags) {}
}
