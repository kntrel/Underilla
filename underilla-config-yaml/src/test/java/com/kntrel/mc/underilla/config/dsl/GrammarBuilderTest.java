package com.kntrel.mc.underilla.config.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GrammarBuilderTest {

    @Test
    void buildsAndCompilesCustomDefinitionsAlongsideStandardOnes() {
        AtomicInteger negations = new AtomicInteger();
        Grammar<Context> grammar = Grammar.with(Context.class)
                .operation(Boolean.class, Boolean.class, Boolean.class,
                        Pattern.keyword("xor"), 15, (a, b) -> a ^ b)
                .literal(Boolean.class, Pattern.keyword("yes"), text -> true)
                .transformation(Boolean.class, Boolean.class, Pattern.keyword("negate"), value -> {
                    negations.incrementAndGet();
                    return !value;
                })
                .runtime(Boolean.class, Pattern.literal("active"), Context::active)
                .runtime(Integer.class, Pattern.literal("x"), Context::x)
                .runtime(new TypeToken<Set<String>>() {}, Pattern.literal("tags"), Context::tags)
                .standard()
                .done();
        Compiler<Context> compiler = Compiler.of(grammar);
        var expression = compiler.compile("negate active xor yes", Boolean.class);

        assertEquals(0, negations.get());
        assertTrue(expression.apply(new Context(true, 63, Set.of("caves"))));
        assertFalse(expression.apply(new Context(false, 63, Set.of("caves"))));
        assertEquals(2, negations.get());
        assertTrue(compiler.compile("x+1 <= 64 and 'caves' in tags", Boolean.class)
                .apply(new Context(true, 63, Set.of("caves"))));
    }

    @Test
    void acceptsParameterizedContextTokens() {
        TypeToken<Integer> integer = TypeToken.of(Integer.class);
        TypeToken<Boolean> bool = TypeToken.of(Boolean.class);
        Grammar<Map<String, Integer>> grammar = Grammar.with(new TypeToken<Map<String, Integer>>() {})
                .standard()
                .literal(integer, Pattern.keyword("one"), text -> 1)
                .operation(integer, integer, integer, Pattern.keyword("plus"), Integer::sum)
                .operation(integer, integer, integer, Pattern.keyword("times"), 60, (a, b) -> a * b)
                .operation(Integer.class, Integer.class, Integer.class, Pattern.keyword("minus"), (a, b) -> a - b)
                .transformation(bool, bool, Pattern.keyword("negate"), value -> !value)
                .runtime(integer, Pattern.literal("x"), values -> values.get("x"))
                .done();

        assertEquals(5, Compiler.of(grammar).compile("x+2", Integer.class).apply(Map.of("x", 3)));
        assertEquals(5, Compiler.of(grammar).compile("x plus one times 2", Integer.class).apply(Map.of("x", 3)));
        assertEquals(2, Compiler.of(grammar).compile("(x plus one) minus 2", Integer.class).apply(Map.of("x", 3)));
        assertFalse(Compiler.of(grammar).compile("negate true", Boolean.class).apply(Map.of()));
    }

    @Test
    void repeatedStandardCallsAreIdempotentAndDoneCreatesIndependentSnapshots() {
        Grammar.Builder<Void> builder = Grammar.with(Void.class).standard();
        Grammar<Void> first = builder.done();
        TypeLiteral<Integer> extra = TypeLiteral.of(Integer.class, Pattern.keyword("one"), text -> 1);
        Grammar<Void> second = builder.standard().literal(extra).done();

        assertEquals(first.operations(), second.operations());
        assertEquals(first.transformations(), second.transformations());
        assertEquals(first.literals().size() + 1, second.literals().size());
        assertEquals(3, Compiler.of(first).compile("1+2", Integer.class).apply(null));
        assertThrows(CompilationException.class, () -> Compiler.of(first).compile("one", Integer.class));
        assertEquals(2, Compiler.of(second).compile("one+1", Integer.class).apply(null));
        assertThrows(UnsupportedOperationException.class, () -> second.transformations().clear());
        assertThrows(CompilationException.class, () -> Compiler.of(Grammar.with(Void.class).done()).compile("1", Integer.class));
    }

    @Test
    void rebindingPreservesTransformationsAndLeavesOriginalRuntimeValuesIntact() {
        Grammar<Context> original = Grammar.with(Context.class).standard()
                .runtime(RuntimeValue.of(Boolean.class, Pattern.literal("active"), Context::active)).done();
        RuntimeValue<Integer> positive = RuntimeValue.of(Boolean.class, Pattern.literal("active"), value -> value > 0);
        Grammar<Integer> rebound = original.withRuntimeValues(List.of(positive));
        Grammar<Void> contextless = original.withoutRuntimeValues();

        assertSame(original.transformations().getFirst(), rebound.transformations().getFirst());
        assertTrue(Compiler.of(rebound).compile("not active", Boolean.class).apply(-1));
        assertFalse(Compiler.of(original).compile("not active", Boolean.class).apply(new Context(true, 0, Set.of())));
        assertFalse(Compiler.of(contextless).compile("!true", Boolean.class).apply(null));
        assertEquals(1, original.runtimeValues().size());
    }

    @Test
    void rejectsNullBuilderInputsAtRegistration() {
        assertThrows(NullPointerException.class, () -> Grammar.with((Class<Void>) null));
        assertThrows(NullPointerException.class, () -> Grammar.with((TypeToken<Void>) null));
        Grammar.Builder<Void> builder = Grammar.with(Void.class);
        assertThrows(NullPointerException.class, () -> builder.operation(null));
        assertThrows(NullPointerException.class, () -> builder.literal(null));
        assertThrows(NullPointerException.class, () -> builder.transformation(null));
        assertThrows(NullPointerException.class, () -> builder.runtime(null));
    }

    private record Context(boolean active, int x, Set<String> tags) {}
}
