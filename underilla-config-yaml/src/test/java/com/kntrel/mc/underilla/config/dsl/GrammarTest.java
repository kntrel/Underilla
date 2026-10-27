package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.kntrel.mc.underilla.config.dsl.*;
import org.junit.jupiter.api.Test;

class GrammarTest {

    private static final TypeLiteral<Integer> ONE = TypeLiteral.of(Integer.class, Pattern.literal("1"), Integer::valueOf);
    private static final Operation<Integer, Integer, Integer> ADD = Operation.of(
            Integer.class, Integer.class, Integer.class, Pattern.literal("+"), Integer::sum);

    @Test
    void reusesLanguageDefinitionsAndReplacesRuntimeValuesAcrossContextTypes() throws Exception {
        RuntimeValue<String> length = RuntimeValue.of(Integer.class, Pattern.literal("x"), String::length);
        RuntimeValue<String> originalOnly = RuntimeValue.of(String.class, Pattern.literal("original"), text -> text);
        Grammar<String> original = new Grammar<>(List.of(ONE), List.of(ADD), List.of(length, originalOnly));
        RuntimeValue<Integer> integer = RuntimeValue.of(Integer.class, Pattern.literal("x"), value -> value);
        List<RuntimeValue<Integer>> replacements = new ArrayList<>(List.of(integer));

        Grammar<Integer> copy = original.withRuntimeValues(replacements);
        replacements.clear();

        assertSame(ONE, copy.literals().getFirst());
        assertSame(ADD, copy.operations().getFirst());
        assertEquals(List.of(integer), copy.runtimeValues());
        assertEquals(List.of(length, originalOnly), original.runtimeValues());
        assertEquals(4, Compiler.of(original).compile("x+1", Integer.class).apply("abc"));
        assertEquals(8, Compiler.of(copy).compile("x+1", Integer.class).apply(7));
        assertThrows(DslCompilationException.class, () -> Compiler.of(copy).compile("original", String.class));
    }

    @Test
    void createsContextlessGrammarWithoutCarryingOverRuntimeValues() throws Exception {
        Grammar<String> original = new Grammar<>(List.of(ONE), List.of(ADD),
                List.of(RuntimeValue.of(Integer.class, Pattern.literal("x"), String::length)));

        Grammar<Void> contextless = original.withoutRuntimeValues();

        assertTrue(contextless.runtimeValues().isEmpty());
        assertEquals(2, Compiler.of(contextless).compile("1+1", Integer.class).apply(null));
        assertThrows(DslCompilationException.class, () -> Compiler.of(contextless).compile("x", Integer.class));
        assertEquals(1, original.runtimeValues().size());
    }

    @Test
    void snapshotsCollectionsInIterationOrderAndExposesImmutableLists() throws Exception {
        TypeLiteral<Integer> two = TypeLiteral.of(Integer.class, Pattern.literal("2"), Integer::valueOf);
        Set<TypeLiteral<Integer>> literals = new LinkedHashSet<>(List.of(ONE, two));
        Set<Operation<Integer, Integer, Integer>> operations = new LinkedHashSet<>(List.of(ADD));
        RuntimeValue<String> length = RuntimeValue.of(Integer.class, Pattern.literal("x"), String::length);
        Set<RuntimeValue<String>> runtimeValues = new LinkedHashSet<>(List.of(length));

        Grammar<String> grammar = new Grammar<>(literals, operations, runtimeValues);
        literals.clear();
        operations.clear();
        runtimeValues.clear();

        assertEquals(List.of(ONE, two), grammar.literals());
        assertEquals(List.of(ADD), grammar.operations());
        assertEquals(List.of(length), grammar.runtimeValues());
        assertThrows(UnsupportedOperationException.class, () -> grammar.literals().clear());
        assertThrows(UnsupportedOperationException.class, () -> grammar.operations().clear());
        assertThrows(UnsupportedOperationException.class, () -> grammar.runtimeValues().clear());
    }

    @Test
    void rejectsNullCollectionsAndDefinitionsWhenConstructingOrRebinding() throws Exception {
        Grammar<Void> grammar = new Grammar<>(List.of(ONE), List.of(ADD));
        assertThrows(NullPointerException.class, () -> new Grammar<>(null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new Grammar<>(List.of(), null, List.of()));
        assertThrows(NullPointerException.class, () -> new Grammar<>(List.of(), List.of(), null));
        assertThrows(NullPointerException.class, () -> grammar.withRuntimeValues(null));
        assertThrows(NullPointerException.class, () -> grammar.withRuntimeValues(Arrays.asList((RuntimeValue<Void>) null)));
    }
}
