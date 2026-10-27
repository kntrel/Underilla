package com.kntrel.mc.underilla.config.dsl.exception;

import com.kntrel.mc.underilla.config.dsl.DefinitionSignature;

import java.util.List;
import java.util.Objects;

/** Unchecked registration/contract error, distinct from invalid DSL expressions. */
public final class InvalidGrammarException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String expression;
    private final int cursor;
    private final int end;
    private final List<DefinitionSignature> definitions;

    public InvalidGrammarException(String message, String expression, int cursor, int end, List<DefinitionSignature> definitions) {
        super(message);
        this.expression = Objects.requireNonNull(expression, "expression");
        if (cursor < 0 || end < cursor || end > expression.length()) throw new IllegalArgumentException("Invalid expression span");
        this.cursor = cursor;
        this.end = end;
        this.definitions = List.copyOf(definitions);
    }
    public String expression() { return expression; }
    public int cursor() { return cursor; }
    public int end() { return end; }
    public List<DefinitionSignature> definitions() { return definitions; }
}
