package com.kntrel.mc.underilla.config.dsl.exception;

import com.kntrel.mc.underilla.config.dsl.DefinitionSignature;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Unchecked registration/contract error, distinct from invalid DSL expressions. */
public final class InvalidGrammarException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    public enum Reason { CONFLICTING_PRECEDENCE, INVALID_MATCH_LENGTH }
    private final String expression;
    private final int cursor;
    private final int end;
    private final List<DefinitionSignature> definitions;
    private final Reason reason;
    private final List<Integer> precedences;
    private final Integer matchLength;

    public InvalidGrammarException(String expression, int cursor, int end,
            List<DefinitionSignature> definitions, List<Integer> precedences) {
        this(expression, cursor, end, definitions, Reason.CONFLICTING_PRECEDENCE, precedences, null);
    }

    public InvalidGrammarException(String expression, int cursor, int end, DefinitionSignature definition, int matchLength) {
        this(expression, cursor, end, List.of(definition), Reason.INVALID_MATCH_LENGTH, List.of(), matchLength);
    }

    private InvalidGrammarException(String expression, int cursor, int end, List<DefinitionSignature> definitions,
            Reason reason, List<Integer> precedences, Integer matchLength) {
        this.expression = Objects.requireNonNull(expression, "expression");
        if (cursor < 0 || end < cursor || end > expression.length()) throw new IllegalArgumentException("Invalid expression span");
        this.cursor = cursor;
        this.end = end;
        this.definitions = List.copyOf(definitions);
        this.reason = reason;
        this.precedences = List.copyOf(precedences);
        this.matchLength = matchLength;
    }
    public String expression() { return expression; }
    public int cursor() { return cursor; }
    public int end() { return end; }
    public List<DefinitionSignature> definitions() { return definitions; }
    public Reason reason() { return reason; }
    public List<Integer> precedences() { return precedences; }
    public OptionalInt matchLength() { return matchLength == null ? OptionalInt.empty() : OptionalInt.of(matchLength); }

    @Override
    public String getMessage() {
        String description = switch (reason) {
            case CONFLICTING_PRECEDENCE -> "Operator overloads must agree on precedence at " + cursor
                    + " for '" + Diagnostics.token(expression, cursor, end) + "'; found " + precedences;
            case INVALID_MATCH_LENGTH -> "Pattern returned match length " + matchLength + " at " + cursor
                    + ", longer than the remaining source (" + (expression.length() - cursor) + ")";
        };
        return Diagnostics.render(description + "; definitions: " + Diagnostics.candidates(definitions), expression, cursor, end);
    }
}
