package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;
import com.kntrel.mc.underilla.config.dsl.exception.IncompatibleTypesException;
import com.kntrel.mc.underilla.config.dsl.exception.InvalidGrammarException;
import com.kntrel.mc.underilla.config.dsl.exception.UnexpectedTokenException;
import com.kntrel.mc.underilla.config.dsl.exception.UnresolvedOperandException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CompilerTest {

    private static final TypeToken<Set<String>> TAGS = new TypeToken<>() {
    };
    private static final Grammar<Void> ARITHMETIC = Grammar.with(Void.class).standard().done();

    @Test
    void emptyListsInferTheirTypeFromTheExpectedResult() throws Exception {
        Compiler<Void> compiler = new Compiler<>(ARITHMETIC);
        assertEquals(List.of(), compiler.compile("[]", new TypeToken<List<String>>() {}).apply(null));
        assertEquals(List.of(), compiler.compile("[ ]", new TypeToken<java.util.Collection<Integer>>() {}).apply(null));
        assertEquals(List.of(), compiler.compile("[]", new TypeToken<Iterable<? extends Number>>() {}).apply(null));
        assertEquals(List.of(), compiler.compile("[]", new TypeToken<List<? super Integer>>() {}).apply(null));
        assertEquals(List.of(), compiler.compile("[]", Object.class).apply(null));
        assertEquals(List.of(), compiler.compile("[]", List.class).apply(null));
        assertFalse(compiler.compile("1 in []", Boolean.class).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("[]", String.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("[]", new TypeToken<Set<String>>() {}));
        assertThrows(UnsupportedOperationException.class,
                () -> compiler.compile("[]", new TypeToken<List<String>>() {}).apply(null).add("ice"));
    }

    @Test
    void nestedEmptyListsInferFromSiblingsAndConsumersWithoutWideningConcreteLists() throws Exception {
        Compiler<Void> compiler = new Compiler<>(ARITHMETIC);
        TypeToken<List<List<Integer>>> integers = new TypeToken<>() {};
        assertEquals(List.of(List.of(), List.of(1, 2)), compiler.compile("[[], [1, 2]]", integers).apply(null));
        assertEquals(List.of(List.of(1, 2), List.of()), compiler.compile("[[1, 2], []]", integers).apply(null));
        assertEquals(List.of(List.of(), List.of()), compiler.compile("[[], []]", integers).apply(null));
        assertEquals(List.of(List.of(List.of()), List.of(List.of(1))), compiler.compile("[[[]], [[1]]]",
                new TypeToken<List<List<List<Integer>>>>() {}).apply(null));
        assertEquals(List.of(List.of()), compiler.compile("[[]]",
                new TypeToken<List<List<? extends Number>>>() {}).apply(null));
        assertEquals(List.of(List.of(), List.of(1), List.of(2.5)), compiler.compile("[[], [1], [2.5]]",
                new TypeToken<List<List<? extends Number>>>() {}).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("[[], [1]]",
                new TypeToken<List<List<Number>>>() {}));
    }

    @Test
    void emptyListsRespectOperatorAndTransformationOverloadAmbiguity() throws Exception {
        TypeToken<List<String>> strings = new TypeToken<>() {};
        TypeToken<List<Integer>> integers = new TypeToken<>() {};
        Operation<Boolean, List<String>, Integer> stringCount = Operation.of(TypeToken.of(Boolean.class), strings,
                TypeToken.of(Integer.class), Pattern.keyword("count"), (flag, values) -> values.size());
        Operation<Boolean, List<Integer>, Integer> integerCount = Operation.of(TypeToken.of(Boolean.class), integers,
                TypeToken.of(Integer.class), Pattern.keyword("count"), (flag, values) -> values.size());
        Compiler<Void> single = Compiler.of(Grammar.with(Void.class).standard().operation(stringCount)
                .transformation(Transformation.of(strings, TypeToken.of(Integer.class), Pattern.keyword("size"), List::size)).done());
        assertEquals(0, single.compile("true count []", Integer.class).apply(null));
        assertEquals(0, single.compile("size []", Integer.class).apply(null));
        Compiler<Void> ambiguous = Compiler.of(Grammar.with(Void.class).standard()
                .operation(stringCount).operation(integerCount)
                .transformation(Transformation.of(strings, TypeToken.of(Integer.class), Pattern.keyword("size"), List::size))
                .transformation(Transformation.of(integers, TypeToken.of(Integer.class), Pattern.keyword("size"), List::size)).done());
        assertTrue(assertThrows(DslCompilationException.class,
                () -> ambiguous.compile("true count []", Integer.class)).getMessage().contains("Ambiguous operator"));
        assertTrue(assertThrows(DslCompilationException.class,
                () -> ambiguous.compile("size []", Integer.class)).getMessage().contains("Ambiguous transformation"));
        assertEquals(1, ambiguous.compile("true count [1]", Integer.class).apply(null));
        assertEquals(1, ambiguous.compile("size ['ice']", Integer.class).apply(null));
    }

    @Test
    void listsInferTheirCommonDeclaredTypeWithoutNumericConversion() throws Exception {
        Compiler<Void> compiler = new Compiler<>(ARITHMETIC);
        assertEquals(List.of(24, 75.3, 0.8f), compiler.compile("[24, 75.3, 0.8f]",
                new TypeToken<List<Number>>() {}).apply(null));
        assertEquals(List.of(24, 75), compiler.compile("[24, 75]",
                new TypeToken<List<Integer>>() {}).apply(null));
        assertEquals(List.of(24, "ice"), compiler.compile("[24, 'ice']",
                new TypeToken<List<Object>>() {}).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("[24, 75.3]",
                new TypeToken<List<Double>>() {}));
        assertThrows(DslCompilationException.class, () -> compiler.compile("[24, 75]",
                new TypeToken<List<Number>>() {}));
        assertTrue(compiler.compile("24 in [24, 75]", Boolean.class).apply(null));
    }

    @Test
    void listsSupportExpressionsNestingAndQuotedDelimiters() throws Exception {
        Compiler<Void> compiler = new Compiler<>(ARITHMETIC);
        assertEquals(List.of(7, 20), compiler.compile("([1 + 2 * 3, (2 + 3) * 4])",
                new TypeToken<List<Integer>>() {}).apply(null));
        assertEquals(List.of(List.of(1), List.of(2)), compiler.compile("[[1], [2]]",
                new TypeToken<List<List<Integer>>>() {}).apply(null));
        assertEquals(List.of(List.of(1), List.of(2.5)), compiler.compile("[[1], [2.5]]",
                new TypeToken<List<List<? extends Number>>>() {}).apply(null));
        assertEquals(List.of(",]", "["), compiler.compile("[',]', '[']",
                new TypeToken<List<String>>() {}).apply(null));
    }

    @Test
    void listsUseDeclaredRuntimeTypesAndEvaluateInOrderOnEachCall() throws Exception {
        List<String> events = new ArrayList<>();
        Compiler<Context> compiler = new Compiler<>(ARITHMETIC.withRuntimeValues(List.of(
                RuntimeValue.of(Number.class, Pattern.keyword("x"), ctx -> {
                    events.add("x");
                    return ctx.x();
                }),
                RuntimeValue.of(Integer.class, Pattern.keyword("next"), ctx -> {
                    events.add("next");
                    return ctx.x() + 1;
                }))));
        Function<Context, List<Number>> expression = compiler.compile("[x, next, 2]",
                new TypeToken<List<Number>>() {});
        assertTrue(events.isEmpty());
        List<Number> first = expression.apply(new Context(3, Set.of()));
        assertEquals(List.of(3, 4, 2), first);
        assertEquals(List.of(8, 9, 2), expression.apply(new Context(8, Set.of())));
        assertEquals(List.of("x", "next", "x", "next"), events);
        assertThrows(UnsupportedOperationException.class, () -> first.add(5));
    }

    @Test
    void malformedListsReportTheOffendingSourceSpan() throws Exception {
        Compiler<Void> compiler = new Compiler<>(ARITHMETIC);
        for (String source : List.of("[1,]", "[,1]", "[1,,2]", "[1 2]", "[1", "[", "[[1]")) {
            DslCompilationException error = assertThrows(DslCompilationException.class,
                    () -> compiler.compile(source, Object.class), source);
            assertEquals(source, error.source());
            assertTrue(error.start() >= 0 && error.end() <= source.length(), source);
        }
        DslCompilationException missingComma = assertThrows(DslCompilationException.class,
                () -> compiler.compile("[1 2]", Object.class));
        assertEquals(3, missingComma.start());
        assertEquals(4, missingComma.end());
        DslCompilationException wrongType = assertThrows(DslCompilationException.class,
                () -> compiler.compile("([1])", String.class));
        assertEquals(0, wrongType.start());
        assertEquals(5, wrongType.end());
    }

    @Test
    void respectsPrecedenceGroupingLeftAssociativityAndWhitespace() throws Exception {
        Compiler<Void> compiler = new Compiler<>(ARITHMETIC);

        assertEquals(14, compiler.compile("2+3*4", Integer.class).apply(null));
        assertEquals(20, compiler.compile("(2+3)*4", Integer.class).apply(null));
        assertEquals(5, compiler.compile("10-3-2", Integer.class).apply(null));
        assertEquals(9, compiler.compile("10-(3-2)", Integer.class).apply(null));
        assertEquals(8, compiler.compile("20/5*2", Integer.class).apply(null));
        assertEquals(14, compiler.compile(" \n ( 2 +\t(3 * 4) ) \r", Integer.class).apply(null));
    }

    @Test
    void compilesTheMembershipAndComparisonExampleAgainstChangingContexts() throws Exception {
        Operation<String, Set<String>, Boolean> in = Operation.of(TypeToken.of(String.class), TAGS,
                TypeToken.of(Boolean.class), Pattern.keyword("in"), 5, (value, set) -> set.contains(value));
        Operation<Integer, Integer, Boolean> lessEqual = Operation.of(Integer.class, Integer.class,
                Boolean.class, Pattern.literal("<="), 5, (a, b) -> a <= b);
        Operation<Boolean, Boolean, Boolean> and = Operation.of(Boolean.class, Boolean.class,
                Boolean.class, Pattern.keyword("and"), 0, (a, b) -> a && b);
        Grammar<Void> grammar = new Grammar<>(ARITHMETIC.literals(), List.of(in, lessEqual, and));
        Compiler<Context> compiler = new Compiler<>(grammar.withRuntimeValues(List.of(
                RuntimeValue.of(Integer.class, Pattern.literal("ref.block.x"), Context::x),
                RuntimeValue.of(TAGS, Pattern.literal("ref.biomes.tags"), Context::tags))));
        Function<Context, Boolean> expression = compiler.compile(
                "\"minecraft:caves\" in ref.biomes.tags and ref.block.x <= 64", Boolean.class);

        assertTrue(expression.apply(new Context(64, Set.of("minecraft:caves"))));
        assertFalse(expression.apply(new Context(65, Set.of("minecraft:caves"))));
        assertFalse(expression.apply(new Context(64, Set.of("minecraft:plains"))));
    }

    @Test
    void selectsLongestMatchesAndLeavesQuotedContentToItsPattern() throws Exception {
        Operation<Integer, Integer, Boolean> less = Operation.of(Integer.class, Integer.class,
                Boolean.class, Pattern.literal("<"), (a, b) -> a < b);
        Operation<Integer, Integer, Boolean> lessEqual = Operation.of(Integer.class, Integer.class,
                Boolean.class, Pattern.literal("<="), (a, b) -> a <= b);
        Grammar<Void> grammar = new Grammar<>(ARITHMETIC.literals(), List.of(less, lessEqual));
        Compiler<Context> compiler = new Compiler<>(grammar.withRuntimeValues(List.of(
                RuntimeValue.of(Integer.class, Pattern.literal("ref"), ctx -> -1),
                RuntimeValue.of(Integer.class, Pattern.literal("ref.block.x"), Context::x))));

        assertTrue(compiler.compile("ref.block.x<=64", Boolean.class).apply(new Context(64, Set.of())));
        assertEquals("(has spaces <= and operators)", compiler.compile(
                "\"(has spaces <= and operators)\"", String.class).apply(null));
    }

    @Test
    void parsesLiteralsOnceAndDefersRuntimeAndOperationsUntilEvaluation() throws Exception {
        AtomicInteger parsed = new AtomicInteger();
        AtomicInteger evaluated = new AtomicInteger();
        AtomicInteger operated = new AtomicInteger();
        AtomicInteger matched = new AtomicInteger();
        Pattern digits = Pattern.regex("[0-9]+");
        TypeLiteral<Integer> literal = TypeLiteral.of(Integer.class, src -> {
            matched.incrementAndGet();
            return digits.match(src);
        }, text -> { parsed.incrementAndGet(); return Integer.valueOf(text); });
        Operation<Integer, Integer, Integer> add = Operation.of(Integer.class, Integer.class,
                Integer.class, Pattern.literal("+"), (a, b) -> { operated.incrementAndGet(); return a + b; });
        RuntimeValue<Context> runtime = RuntimeValue.of(Integer.class, Pattern.literal("x"), ctx -> {
            evaluated.incrementAndGet(); return ctx.x();
        });
        Compiler<Context> compiler = Compiler.of(new Grammar<>(List.of(literal), List.of(add), List.of(runtime)));
        Function<Context, Integer> expression = compiler.compile("x + 2", Integer.class);

        assertEquals(1, parsed.get());
        assertEquals(0, evaluated.get());
        assertEquals(0, operated.get());
        int matchesAtCompilation = matched.get();
        assertEquals(5, expression.apply(new Context(3, Set.of())));
        assertEquals(10, expression.apply(new Context(8, Set.of())));
        assertEquals(2, evaluated.get());
        assertEquals(2, operated.get());
        assertEquals(1, parsed.get());
        assertEquals(matchesAtCompilation, matched.get());
    }

    @Test
    void acceptsNumberSubtypesButKeepsTheDeclaredResultType() throws Exception {
        Operation<Number, Number, Number> sum = Operation.of(Number.class, Number.class, Number.class,
                Pattern.literal("+"), (a, b) -> a.doubleValue() + b.doubleValue());
        Compiler<Void> compiler = Compiler.of(new Grammar<>(ARITHMETIC.literals(), List.of(sum)));

        assertEquals(3.5, compiler.compile("1 + 2.5", Number.class).apply(null));
        assertEquals(3.5, compiler.compile("1 + 2.5", Object.class).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("1 + 2.5", Double.class));
        assertEquals(1, compiler.compile("1", Number.class).apply(null));
    }

    @Test
    void choosesTheMostSpecificOverloadRegardlessOfRegistrationOrder() throws Exception {
        Operation<?, ?, ?> addition = standardIntegerAddition();
        Operation<Number, Number, Number> general = Operation.of(Number.class, Number.class, Number.class,
                Pattern.literal("+"), addition.precedence(), (a, b) -> -1);
        for (List<Operation<?, ?, ?>> operations : List.of(
                List.<Operation<?, ?, ?>>of(general, addition), List.<Operation<?, ?, ?>>of(addition, general))) {
            Compiler<Void> compiler = new Compiler<>(new Grammar<>(ARITHMETIC.literals(), operations));
            assertEquals(3, compiler.compile("1+2", Integer.class).apply(null));
        }
    }

    @Test
    void supportsDistinctOperatorOverloadsAndRejectsIncompatibleOperands() throws Exception {
        Compiler<Void> compiler = new Compiler<>(ARITHMETIC);

        assertEquals("ab", compiler.compile("\"a\" + \"b\"", String.class).apply(null));
        assertEquals(3, compiler.compile("1 + 2", Integer.class).apply(null));
        DslCompilationException failure = assertThrows(DslCompilationException.class,
                () -> compiler.compile("1 + \"b\"", Integer.class));
        assertEquals(2, failure.start());
        assertEquals(3, failure.end());
        assertInstanceOf(IncompatibleTypesException.class, failure);
    }

    @Test
    void rejectsEquallySpecificAndIncomparableOperatorOverloads() throws Exception {
        Operation<?, ?, ?> addition = standardIntegerAddition();
        Compiler<Void> duplicated = new Compiler<>(new Grammar<>(ARITHMETIC.literals(), List.of(addition, addition)));
        assertTrue(assertThrows(DslCompilationException.class, () -> duplicated.compile("1+2", Integer.class))
                .getMessage().contains("Ambiguous operator"));

        Operation<Integer, Number, Integer> left = Operation.of(Integer.class, Number.class, Integer.class,
                Pattern.literal("+"), (a, b) -> 1);
        Operation<Number, Integer, Integer> right = Operation.of(Number.class, Integer.class, Integer.class,
                Pattern.literal("+"), (a, b) -> 2);
        Compiler<Void> crossed = new Compiler<>(new Grammar<>(ARITHMETIC.literals(), List.of(left, right)));
        assertTrue(assertThrows(DslCompilationException.class, () -> crossed.compile("1+2", Integer.class))
                .getMessage().contains("Ambiguous operator"));
    }

    @Test
    void rejectsConflictingPrecedenceBeforeResolvingOverloads() throws Exception {
        Operation<?, ?, ?> addition = standardIntegerAddition();
        Operation<String, String, String> other = Operation.of(String.class, String.class, String.class,
                Pattern.literal("+"), addition.precedence() + 1, (a, b) -> a + b);
        Compiler<Void> compiler = new Compiler<>(new Grammar<>(ARITHMETIC.literals(), List.of(addition, other)));
        assertTrue(assertThrows(InvalidGrammarException.class, () -> compiler.compile("1+2", Integer.class))
                .getMessage().contains("agree on precedence"));
    }

    @Test
    void checksGenericOperandAndResultTypesWithoutErasingArguments() throws Exception {
        Operation<String, Set<String>, Boolean> in = Operation.of(TypeToken.of(String.class), TAGS,
                TypeToken.of(Boolean.class), Pattern.keyword("in"), (value, set) -> set.contains(value));
        Compiler<Void> compiler = new Compiler<>(new Grammar<>(ARITHMETIC.literals(), List.of(in), List.of(
                RuntimeValue.of(TAGS, Pattern.literal("tags"), ignored -> Set.of("caves")),
                RuntimeValue.of(new TypeToken<Set<Integer>>() {}, Pattern.literal("numbers"), ignored -> Set.of(1)))));

        assertEquals(Set.of("caves"), compiler.compile("tags", TAGS).apply(null));
        assertTrue(compiler.compile("\"caves\" in tags", Boolean.class).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("numbers", TAGS));
        assertThrows(DslCompilationException.class, () -> compiler.compile("\"caves\" in numbers", Boolean.class));
    }

    @Test
    void supportsGenericSupertypesAndPrimitiveBoxing() throws Exception {
        TypeToken<Iterable<? extends Number>> iterable = new TypeToken<Iterable<? extends Number>>() {};
        Operation<Iterable<? extends Number>, Integer, Boolean> contains = Operation.of(iterable,
                TypeToken.of(Integer.class), TypeToken.of(Boolean.class), Pattern.keyword("contains"),
                (values, sought) -> {
                    for (Number value : values) { if (value.intValue() == sought) { return true; } }
                    return false;
                });
        Compiler<Void> compiler = new Compiler<>(new Grammar<>(ARITHMETIC.literals(), List.of(contains), List.of(
                RuntimeValue.of(new TypeToken<Set<Integer>>() {}, Pattern.literal("numbers"), ignored -> Set.of(1)))));

        assertTrue(compiler.compile("numbers contains 1", boolean.class).apply(null));
        assertEquals(1, compiler.compile("1", int.class).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("1", Long.class));
    }

    @Test
    void ambiguousOperandsFailBeforeInvokingAnyDecoderOrRuntimeValue() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        TypeLiteral<Integer> literal = TypeLiteral.of(Integer.class, Pattern.literal("x"), text -> {
            invocations.incrementAndGet(); return 1;
        });
        RuntimeValue<Void> value = RuntimeValue.of(Integer.class, Pattern.literal("x"), ignored -> {
            invocations.incrementAndGet(); return 2;
        });
        Compiler<Void> compiler = new Compiler<>(new Grammar<>(List.of(literal), List.of(), List.of(value)));

        assertTrue(assertThrows(DslCompilationException.class, () -> compiler.compile("x", Integer.class))
                .getMessage().contains("Ambiguous operand"));
        assertEquals(0, invocations.get());
    }

    @Test
    void reportsSourceSpansForMalformedExpressionsAndResultMismatches() throws Exception {
        Compiler<Void> compiler = new Compiler<>(ARITHMETIC);
        assertError(compiler, "", 0, 0, UnexpectedTokenException.class);
        assertError(compiler, "1 +", 3, 3, UnexpectedTokenException.class);
        assertError(compiler, "1 2", 2, 3, UnexpectedTokenException.class);
        assertError(compiler, "(1", 2, 2, UnexpectedTokenException.class);
        assertError(compiler, "()", 1, 2, UnexpectedTokenException.class);
        assertError(compiler, "1)", 1, 2, UnexpectedTokenException.class);
        assertError(compiler, "?", 0, 1, UnresolvedOperandException.class);
        DslCompilationException typeFailure = assertThrows(DslCompilationException.class,
                () -> compiler.compile("  (1+2) ", Boolean.class));
        assertEquals(2, typeFailure.start());
        assertEquals(7, typeFailure.end());
        assertInstanceOf(IncompatibleTypesException.class, typeFailure);
    }

    @Test
    void groupedNodesRetainFullSourceSpansWithoutEvaluatingRuntimeValues() throws Exception {
        AtomicInteger evaluations = new AtomicInteger();
        RuntimeValue<Context> runtime = RuntimeValue.of(Integer.class, Pattern.literal("x"), context -> {
            evaluations.incrementAndGet();
            return context.x();
        });
        Compiler<Context> compiler = Compiler.of(ARITHMETIC.withRuntimeValues(List.of(runtime)));

        for (String source : List.of("  ((1))  ", "  ((x))  ", "  ((x+1))  ")) {
            DslCompilationException failure = assertThrows(DslCompilationException.class,
                    () -> compiler.compile(source, Boolean.class));
            assertEquals(2, failure.start());
            assertEquals(source.stripTrailing().length(), failure.end());
            assertEquals(source, failure.source());
        }
        assertEquals(0, evaluations.get());
        Function<Context, Integer> expression = compiler.compile("((x+1))", Integer.class);
        assertEquals(0, evaluations.get());
        assertEquals(5, expression.apply(new Context(4, Set.of())));
        assertEquals(1, evaluations.get());
    }

    @Test
    void preservesDecoderAndPatternFailureCausesAndRejectsInvalidMatchLengths() throws Exception {
        IllegalStateException failure = new IllegalStateException("decoder failed");
        TypeLiteral<Integer> broken = TypeLiteral.of(Integer.class, Pattern.literal("x"), text -> { throw failure; });
        Compiler<Void> compiler = new Compiler<>(new Grammar<>(List.of(broken), List.of()));
        assertSame(failure, assertThrows(DslCompilationException.class, () -> compiler.compile("x", Integer.class)).getCause());

        TypeLiteral<Integer> brokenPattern = TypeLiteral.of(Integer.class, src -> { throw failure; }, text -> 1);
        Compiler<Void> badPattern = new Compiler<>(new Grammar<>(List.of(brokenPattern), List.of()));
        assertSame(failure, assertThrows(DslCompilationException.class, () -> badPattern.compile("x", Integer.class)).getCause());

        TypeLiteral<Integer> tooLong = TypeLiteral.of(Integer.class, src -> src.length() + 1, text -> 1);
        Compiler<Void> badLength = new Compiler<>(new Grammar<>(List.of(tooLong), List.of()));
        assertTrue(assertThrows(InvalidGrammarException.class, () -> badLength.compile("x", Integer.class))
                .getMessage().contains("longer than"));
        TypeLiteral<Integer> noMatch = TypeLiteral.of(Integer.class, src -> -1, text -> 0);
        List<TypeLiteral<?>> literals = new ArrayList<>(ARITHMETIC.literals());
        literals.add(noMatch);
        assertEquals(1, new Compiler<Void>(new Grammar<>(literals, List.of()))
                .compile("1", Integer.class).apply(null));
    }

    @Test
    void operationExceptionsRemainDeferredToEvaluation() throws Exception {
        Function<Void, Integer> expression = new Compiler<Void>(ARITHMETIC).compile("1/0", Integer.class);
        assertThrows(ArithmeticException.class, () -> expression.apply(null));
    }

    @Test
    void handlesTheFullIntegerPrecedenceRangeWithoutOverflow() throws Exception {
        Operation<Integer, Integer, Integer> subtract = Operation.of(Integer.class, Integer.class, Integer.class,
                Pattern.literal("-"), Integer.MAX_VALUE, (a, b) -> a - b);
        Operation<Integer, Integer, Integer> add = Operation.of(Integer.class, Integer.class, Integer.class,
                Pattern.literal("+"), Integer.MIN_VALUE, Integer::sum);
        Compiler<Void> compiler = new Compiler<>(new Grammar<>(ARITHMETIC.literals(), List.of(subtract, add)));

        assertEquals(5, compiler.compile("10-3-2", Integer.class).apply(null));
        assertEquals(15, compiler.compile("10+8-3", Integer.class).apply(null));
    }

    @Test
    void snapshotsRuntimeCollectionsAndSharesGrammarAcrossContextTypes() throws Exception {
        List<RuntimeValue<Context>> values = new ArrayList<>();
        values.add(RuntimeValue.of(Integer.class, Pattern.literal("x"), Context::x));
        Compiler<Context> contextCompiler = new Compiler<>(ARITHMETIC.withRuntimeValues(values));
        values.clear();
        Compiler<Integer> integerCompiler = Compiler.of(ARITHMETIC.withRuntimeValues(
                List.of(RuntimeValue.of(Integer.class, Pattern.literal("x"), Function.identity()))));

        assertEquals(5, contextCompiler.compile("x+2", Integer.class).apply(new Context(3, Set.of())));
        assertEquals(9, integerCompiler.compile("x+2", Integer.class).apply(7));
    }

    @Test
    void compilationAndEvaluationHaveIndependentStateAcrossThreads() throws Exception {
        Compiler<Context> compiler = new Compiler<>(ARITHMETIC.withRuntimeValues(
                List.of(RuntimeValue.of(Integer.class, Pattern.literal("x"), Context::x))));
        Function<Context, Integer> shared = compiler.compile("x+1", Integer.class);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int input = i;
                results.add(executor.submit(() -> compiler.compile("x+" + input, Integer.class)
                        .apply(new Context(shared.apply(new Context(input, Set.of())), Set.of()))));
            }
            for (int i = 0; i < results.size(); i++) {
                assertEquals(2 * i + 1, results.get(i).get());
            }
        }
    }

    @Test
    void rejectsNullConstructionAndCompilationInputs() throws Exception {
        assertThrows(NullPointerException.class, () -> new Compiler<Void>(null));
        Compiler<Void> compiler = new Compiler<>(ARITHMETIC);
        assertThrows(NullPointerException.class, () -> compiler.compile(null, Integer.class));
        assertThrows(NullPointerException.class, () -> compiler.compile("1", (Class<Integer>) null));
        assertThrows(NullPointerException.class, () -> compiler.compile("1", (TypeToken<Integer>) null));
    }

    private static Operation<?, ?, ?> standardIntegerAddition() {
        TypeToken<Integer> integer = TypeToken.of(Integer.class);
        return ARITHMETIC.operations().stream()
                .filter(operation -> operation.aType().equals(integer) && operation.bType().equals(integer))
                .filter(operation -> operation.type().equals(integer) && operation.pattern().match(Slice.of("+")) == 1)
                .findFirst().orElseThrow();
    }

    private static void assertError(Compiler<Void> compiler, String source, int start, int end, Class<? extends DslCompilationException> expected) throws Exception {
        DslCompilationException failure = assertThrows(expected, () -> compiler.compile(source, Integer.class));
        assertEquals(source, failure.source());
        assertEquals(start, failure.start());
        assertEquals(end, failure.end());
        assertFalse(RuntimeException.class.isInstance(failure));
    }

    private record Context(int x, Set<String> tags) {}
}
