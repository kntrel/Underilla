package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.dsl.exception.AmbiguousOperandException;
import com.kntrel.mc.underilla.config.dsl.exception.AmbiguousOperatorException;
import com.kntrel.mc.underilla.config.dsl.exception.AmbiguousTransformationException;
import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;
import com.kntrel.mc.underilla.config.dsl.exception.IncompatibleTypesException;
import com.kntrel.mc.underilla.config.dsl.exception.OperandBindingException;
import com.kntrel.mc.underilla.config.dsl.exception.PatternEvaluationException;
import com.kntrel.mc.underilla.config.dsl.exception.UnexpectedTokenException;
import com.kntrel.mc.underilla.config.dsl.exception.UnresolvedOperandException;

import static org.junit.jupiter.api.Assertions.*;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DslCompilationExceptionTest {
    private static final Compiler<Void> STANDARD = Compiler.of(Grammar.with(Void.class).standard().done());

    @Test
    void unresolvedOperandMessagesShowTheFullOperandAndUnderlineItsSourceSpan() {
        var failure = assertThrows(UnresolvedOperandException.class,
                () -> STANDARD.compile("1 + mystery", Integer.class));
        assertEquals("mystery", failure.operand());
        assertEquals("Unrecognized operand at 4: mystery\n1 + mystery\n    ^~~~~~~", failure.getMessage());
    }

    @Test
    void diagnosticsPointToTheRelevantLineExpandTabsAndMarkEndOfInput() {
        String source = "1 +\r\n\tmystery";
        var failure = assertThrows(UnresolvedOperandException.class, () -> STANDARD.compile(source, Integer.class));
        assertEquals(source.indexOf("mystery"), failure.cursor());
        assertTrue(failure.getMessage().endsWith("\n    mystery\n    ^~~~~~~"), failure.getMessage());
        var eof = assertThrows(UnexpectedTokenException.class, () -> STANDARD.compile("(1", Integer.class));
        assertTrue(eof.getMessage().contains("end of input at 2; expected ); delimiter opened at 0"), eof.getMessage());
        assertTrue(eof.getMessage().endsWith("\n(1\n  ^"), eof.getMessage());
        var empty = assertThrows(UnexpectedTokenException.class, () -> STANDARD.compile("", Integer.class));
        assertTrue(empty.getMessage().endsWith("\n\n^"), empty.getMessage());
    }

    @Test
    void checkedContractIsDeclaredByBothCompileOverloads() throws Exception {
        assertEquals(Exception.class, DslCompilationException.class.getSuperclass());
        assertEquals(List.of(DslCompilationException.class), List.of(
                Compiler.class.getMethod("compile", String.class, Class.class).getExceptionTypes()));
        assertEquals(List.of(DslCompilationException.class), List.of(
                Compiler.class.getMethod("compile", String.class, TypeToken.class).getExceptionTypes()));
    }

    @Test
    void syntaxFailuresExposeExpectedTokensEofAndOpeningDelimiter() {
        var missing = assertThrows(UnexpectedTokenException.class, () -> STANDARD.compile("[1", Object.class));
        assertEquals("[1", missing.expression());
        assertEquals(2, missing.cursor());
        assertEquals(2, missing.end());
        assertTrue(missing.endOfInput());
        assertEquals("", missing.actualToken());
        assertEquals(List.of(",", "]"), missing.expectedTokens());
        assertEquals(0, missing.openingCursor().orElseThrow());
        var comma = assertThrows(UnexpectedTokenException.class, () -> STANDARD.compile("[1 2]", Object.class));
        assertEquals("2", comma.actualToken());
        assertFalse(comma.endOfInput());
        var group = assertThrows(UnexpectedTokenException.class, () -> STANDARD.compile("(1", Object.class));
        assertEquals(List.of(")"), group.expectedTokens());
        assertEquals(0, group.openingCursor().orElseThrow());
        var operand = assertThrows(UnexpectedTokenException.class, () -> STANDARD.compile("1+", Object.class));
        assertEquals(List.of("operand"), operand.expectedTokens());
        assertTrue(operand.openingCursor().isEmpty());
        var unknown = assertThrows(UnresolvedOperandException.class, () -> STANDARD.compile("?", Object.class));
        assertEquals("?", unknown.operand());
        assertEquals(0, unknown.cursor());
    }

    @Test
    void typeFailuresExposeActualTypesExpectedSignaturesAndCandidates() {
        var result = assertThrows(IncompatibleTypesException.class, () -> STANDARD.compile("1", String.class));
        assertEquals(List.of(TypeToken.of(Integer.class)), result.actualTypes());
        assertEquals(List.of(List.of(TypeToken.of(String.class))), result.expectedTypes());
        assertTrue(result.candidates().isEmpty());
        var operator = assertThrows(IncompatibleTypesException.class, () -> STANDARD.compile("1+'ice'", Object.class));
        assertEquals(1, operator.cursor());
        assertEquals(2, operator.end());
        assertEquals(List.of(TypeToken.of(Integer.class), TypeToken.of(String.class)), operator.actualTypes());
        assertFalse(operator.candidates().isEmpty());
        assertEquals(operator.expectedTypes(), operator.candidates().stream().map(DefinitionSignature::operandTypes).toList());
        assertTrue(operator.candidates().stream().allMatch(definition -> definition.kind() == DefinitionSignature.Kind.OPERATOR));
        assertTrue(operator.getMessage().contains("operator '+' at 1: found (java.lang.Integer, java.lang.String)"));
        assertTrue(operator.getMessage().contains("expected ("));
        assertTrue(operator.getMessage().endsWith("\n1+'ice'\n ^"));
        var transformation = assertThrows(IncompatibleTypesException.class, () -> STANDARD.compile("not 1", Object.class));
        assertEquals(List.of(List.of(TypeToken.of(Boolean.class))), transformation.expectedTypes());
    }

    @Test
    void ambiguitiesExposeMatchingDefinitionsWithoutComputingOperands() {
        AtomicInteger parsed = new AtomicInteger();
        var literal = TypeLiteral.of(Integer.class, Pattern.literal("x"), text -> parsed.incrementAndGet());
        var operands = Compiler.of(new Grammar<Void>(List.of(literal, literal), List.of()));
        var ambiguous = assertThrows(AmbiguousOperandException.class, () -> operands.compile("x", Object.class));
        assertEquals(2, ambiguous.candidates().size());
        assertSame(literal.pattern(), ambiguous.candidates().getFirst().pattern());
        assertEquals(0, parsed.get());
        var operation = Operation.of(Integer.class, Integer.class, Integer.class, Pattern.literal("+"), 50, Integer::sum);
        var operators = Compiler.of(Grammar.with(Void.class).standard().operation(operation).operation(operation).done());
        var operator = assertThrows(AmbiguousOperatorException.class, () -> operators.compile("1+2", Object.class));
        assertEquals(1, operator.cursor());
        assertTrue(operator.candidates().size() >= 2);
        assertTrue(operator.getMessage().contains("Ambiguous operator at 1: '+' for (java.lang.Integer, java.lang.Integer)"));
        assertTrue(operator.getMessage().contains("matching definitions: operator (java.lang.Integer, java.lang.Integer) -> java.lang.Integer"));
        var transformation = Transformation.of(Integer.class, Integer.class, Pattern.keyword("twice"), value -> value * 2);
        var transformations = Compiler.of(Grammar.with(Void.class).standard()
                .transformation(transformation).transformation(transformation).done());
        var prefix = assertThrows(AmbiguousTransformationException.class, () -> transformations.compile("twice 1", Object.class));
        assertEquals(2, prefix.candidates().size());
        assertEquals(0, prefix.cursor());
        assertEquals(5, prefix.end());
    }

    @Test
    void callbackFailuresPreserveDefinitionIdentityCauseAndSource() {
        var overflow = assertThrows(OperandBindingException.class, () -> STANDARD.compile("2147483648", Object.class));
        assertInstanceOf(NumberFormatException.class, overflow.getCause());
        assertEquals(DefinitionSignature.Kind.LITERAL, overflow.definition().kind());
        assertEquals(10, overflow.end());
        assertTrue(overflow.getMessage().contains("operand '2147483648' at 0 as literal () -> java.lang.Integer"));
        assertTrue(overflow.getMessage().contains("NumberFormatException"));
        var cause = new IllegalStateException("broken runtime binding");
        RuntimeValue<Void> runtime = new RuntimeValue<>() {
            @Override public Pattern pattern() { return Pattern.literal("ref"); }
            @Override public Bound<Void, ?> bind(String matched) { throw cause; }
        };
        var compiler = Compiler.of(Grammar.with(Void.class).runtime(runtime).done());
        var binding = assertThrows(OperandBindingException.class, () -> compiler.compile("ref", Object.class));
        assertSame(cause, binding.getCause());
        assertEquals(DefinitionSignature.Kind.RUNTIME_VALUE, binding.definition().kind());
        Pattern broken = slice -> { throw cause; };
        var patternCompiler = Compiler.of(Grammar.with(Void.class).literal(Integer.class, broken, text -> 1).done());
        var pattern = assertThrows(PatternEvaluationException.class, () -> patternCompiler.compile("x", Object.class));
        assertSame(cause, pattern.getCause());
        assertSame(broken, pattern.definition().pattern());
        assertEquals("x", pattern.expression());
        assertEquals(0, pattern.cursor());
        assertTrue(pattern.getMessage().contains("Pattern matching failed at 0 for literal () -> java.lang.Integer"));
        assertTrue(pattern.getMessage().contains("IllegalStateException: broken runtime binding"));
    }
}
