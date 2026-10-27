package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.dsl.*;
import org.junit.jupiter.api.Test;

class GrammarStdTest {

    private final Compiler<Void> compiler = Compiler.of(Grammar.with(Void.class).standard().done());

    @Test
    void parsesNumericLiteralsAndPromotesArithmeticWithDeclaredResultTypes() throws Exception {
        assertEquals(7, compiler.compile("1+2*3", Integer.class).apply(null));
        assertEquals(5, compiler.compile("10-3-2", Integer.class).apply(null));
        assertEquals(-3, compiler.compile("-5+2", Integer.class).apply(null));
        assertEquals(3, compiler.compile("1--2", Integer.class).apply(null));
        assertEquals(3L, compiler.compile("1+2L", Long.class).apply(null));
        assertEquals(3L, compiler.compile("1L+2", Long.class).apply(null));
        assertEquals(3.5, compiler.compile("1+2.5", Double.class).apply(null));
        assertEquals(3.5, compiler.compile("1.5+2L", Double.class).apply(null));
        assertEquals(101.0, compiler.compile("1e2+.5+.5", Double.class).apply(null));
        assertEquals(5.0, compiler.compile("2.+3.", Double.class).apply(null));
        assertEquals(2, compiler.compile("5/2", Integer.class).apply(null));
        assertEquals(2L, compiler.compile("5L/2", Long.class).apply(null));
        assertEquals(2.5, compiler.compile("5/2.0", Double.class).apply(null));
        assertEquals(1, compiler.compile("5%2", Integer.class).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("1+2L", Integer.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("2147483648", Integer.class));
        assertEquals(2147483648L, compiler.compile("2147483648L", Long.class).apply(null));
        var divideByZero = compiler.compile("1/0", Integer.class);
        assertThrows(ArithmeticException.class, () -> divideByZero.apply(null));
    }

    @Test
    void arithmeticOnStaticallyBroaderNumberInputsHasADoubleResult() throws Exception {
        Grammar<Number> grammar = Grammar.with(Number.class).standard()
                .runtime(RuntimeValue.of(Number.class, Pattern.literal("x"), value -> value)).done();
        var expression = Compiler.of(grammar).compile("x+1", Double.class);

        assertEquals(6.0, expression.apply(5));
        assertEquals(6.5, expression.apply(5.5));
        assertThrows(DslCompilationException.class, () -> Compiler.of(grammar).compile("x+1", Integer.class));
    }

    @Test
    void registersEveryConcreteArithmeticPairWithTheCorrectResultClass() throws Exception {
        String[] values = {"6b", "6s", "6", "6L", "6f", "6.0"};
        String[] rightValues = {"2b", "2s", "2", "2L", "2f", "2.0"};
        String[] operators = {"+", "-", "*", "/", "%"};
        double[] expected = {8, 4, 12, 3, 0};
        Class<?>[] types = {Integer.class, Integer.class, Integer.class, Long.class, Float.class, Double.class};
        for (int a = 0; a < values.length; a++) {
            for (int b = 0; b < values.length; b++) {
                for (int operator = 0; operator < operators.length; operator++) {
                    Number value = compiler.compile(values[a] + operators[operator] + rightValues[b], Number.class).apply(null);
                    assertEquals(types[Math.max(a, b)], value.getClass());
                    assertEquals(expected[operator], value.doubleValue());
                }
            }
        }
    }

    @Test
    void parsesExplicitNumericTypesAndValidatesNarrowLiteralRanges() throws Exception {
        assertEquals(Byte.valueOf((byte) 127), compiler.compile("127b", Byte.class).apply(null));
        assertEquals(Byte.valueOf((byte) -128), compiler.compile("-128B", byte.class).apply(null));
        assertEquals(Short.valueOf((short) 32767), compiler.compile("32767s", Short.class).apply(null));
        assertEquals(Short.valueOf((short) -32768), compiler.compile("-32768S", short.class).apply(null));
        assertEquals(1.5f, compiler.compile("1.5f", Float.class).apply(null));
        assertEquals(2f, compiler.compile("2F", float.class).apply(null));
        assertEquals(-125f, compiler.compile("-1.25e2f", Float.class).apply(null));
        assertEquals(.5f, compiler.compile(".5f", Float.class).apply(null));
        assertEquals(2.0, compiler.compile("2D", Double.class).apply(null));
        assertEquals(1.5, compiler.compile("1.5d", Double.class).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("128b", Byte.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("-129b", Byte.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("32768s", Short.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("-32769s", Short.class));
    }

    @Test
    void promotesSmallIntegersAndUsesFloatPrecisionForFloatResults() throws Exception {
        assertEquals(128, compiler.compile("127b+1b", Integer.class).apply(null));
        assertEquals(32768, compiler.compile("32767s+1s", Integer.class).apply(null));
        assertEquals(2, compiler.compile("5s/2b", Integer.class).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("1b+2b", Byte.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("1s+2s", Short.class));
        assertEquals(2.5f, compiler.compile("5f/2", Float.class).apply(null));
        assertEquals(1.5f, compiler.compile("5.5f%2s", Float.class).apply(null));
        assertEquals(16777216f, compiler.compile("16777216f+1", Float.class).apply(null));
        assertEquals(16777216f, compiler.compile("16777217L+0f", Float.class).apply(null));
        assertEquals(16777217.0, compiler.compile("16777216f+1.0", Double.class).apply(null));
        assertEquals(Float.POSITIVE_INFINITY, compiler.compile("1f/0f", Float.class).apply(null));
        assertFalse(compiler.compile("(0f/0f)==(0f/0f)", Boolean.class).apply(null));
        assertTrue(compiler.compile("(1f/0f)>1f", Boolean.class).apply(null));
    }

    @Test
    void acceptsPrimitiveByteShortAndFloatRuntimeValues() throws Exception {
        Grammar<SmallNumbers> grammar = Grammar.with(SmallNumbers.class).standard()
                .runtime(RuntimeValue.of(byte.class, Pattern.keyword("b"), SmallNumbers::b))
                .runtime(RuntimeValue.of(short.class, Pattern.keyword("s"), SmallNumbers::s))
                .runtime(RuntimeValue.of(float.class, Pattern.keyword("f"), SmallNumbers::f))
                .done();
        Compiler<SmallNumbers> numeric = Compiler.of(grammar);
        SmallNumbers values = new SmallNumbers((byte) 2, (short) 3, 1.5f);

        assertEquals(5, numeric.compile("b+s", int.class).apply(values));
        assertEquals(4.5f, numeric.compile("s+f", float.class).apply(values));
        assertEquals(4.5, numeric.compile("b+f+1.0", double.class).apply(values));
        assertTrue(numeric.compile("b<s and f<3f and b==2b", Boolean.class).apply(values));
    }

    @Test
    void comparesNumbersWithoutLosingIntegralPrecisionAndSupportsEquality() throws Exception {
        assertTrue(compiler.compile("9007199254740993L > 9007199254740992L", Boolean.class).apply(null));
        assertFalse(compiler.compile("9007199254740993L == 9007199254740992L", Boolean.class).apply(null));
        assertTrue(compiler.compile("1 == 1.0", Boolean.class).apply(null));
        assertTrue(compiler.compile("1 = 1L", Boolean.class).apply(null));
        assertFalse(compiler.compile("1 != 1.0", Boolean.class).apply(null));
        assertTrue(compiler.compile("2 >= 2 and 2 <= 3 and 1 < 2", Boolean.class).apply(null));
        assertTrue(compiler.compile("'a' < 'b' and 'a' != 'b'", Boolean.class).apply(null));
        assertTrue(compiler.compile("'a' == 'a' and true != false", Boolean.class).apply(null));
    }

    @Test
    void numericComparisonCallbacksPreserveNaNAndInfinitySemantics() throws Exception {
        Grammar<Double> grammar = Grammar.with(Double.class).standard()
                .runtime(RuntimeValue.of(Double.class, Pattern.literal("x"), value -> value)).done();
        Compiler<Double> numeric = Compiler.of(grammar);

        assertFalse(numeric.compile("x==x", Boolean.class).apply(Double.NaN));
        assertTrue(numeric.compile("x!=x", Boolean.class).apply(Double.NaN));
        for (String operator : new String[] {"<", "<=", ">", ">="}) {
            assertFalse(numeric.compile("x" + operator + "1.0", Boolean.class).apply(Double.NaN));
        }
        assertTrue(numeric.compile("x==x", Boolean.class).apply(Double.POSITIVE_INFINITY));
        assertTrue(numeric.compile("x>1.0", Boolean.class).apply(Double.POSITIVE_INFINITY));
        assertTrue(numeric.compile("x<1.0", Boolean.class).apply(Double.NEGATIVE_INFINITY));
    }

    @Test
    void providesBooleanNegationWithPrefixBindingAndParenthesizedSubjects() throws Exception {
        assertFalse(compiler.compile("!true", Boolean.class).apply(null));
        assertTrue(compiler.compile("not false", Boolean.class).apply(null));
        assertTrue(compiler.compile("!!true", Boolean.class).apply(null));
        assertTrue(compiler.compile("not not true", Boolean.class).apply(null));
        assertTrue(compiler.compile("not(1 > 2)", Boolean.class).apply(null));
        assertTrue(compiler.compile("!false and false or true", Boolean.class).apply(null));
        assertFalse(compiler.compile("not true and false", Boolean.class).apply(null));
        assertTrue(compiler.compile("not(true and false)", Boolean.class).apply(null));
        assertTrue(compiler.compile("true || false && false", Boolean.class).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("not 1", Boolean.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("!", Boolean.class));
        DslCompilationException failure = assertThrows(DslCompilationException.class,
                () -> compiler.compile("  not(false)  ", Integer.class));
        assertEquals(2, failure.start());
        assertEquals(12, failure.end());
    }

    @Test
    void decodesQuotedStringsEscapesAndConcatenation() throws Exception {
        assertEquals("minecraft:caves with spaces", compiler.compile("\"minecraft:caves with spaces\"", String.class).apply(null));
        assertEquals("a\nb\t\"\\", compiler.compile("\"a\\nb\\t\\\"\\\\\"", String.class).apply(null));
        assertEquals("a", compiler.compile("\"\\u0061\"", String.class).apply(null));
        assertEquals("it's fine", compiler.compile("'it\\'s fine'", String.class).apply(null));
        assertEquals("ab", compiler.compile("'a' + \"b\"", String.class).apply(null));
        assertThrows(DslCompilationException.class, () -> compiler.compile("'\\q'", String.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("'\\u12'", String.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("'\\u+061'", String.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("'unterminated", String.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("trueish", Boolean.class));
    }

    private record SmallNumbers(byte b, short s, float f) {}
}
