package com.kntrel.mc.underilla.config.dsl.exception;

import java.util.Objects;

/** A checked compilation failure with a half-open UTF-16 source span. */
public abstract class DslCompilationException extends Exception {
    private static final long serialVersionUID = 1L;
    private final String expression;
    private final int cursor;
    private final int end;

    protected DslCompilationException(String message, String expression, int cursor, int end, Throwable cause) {
        super(message, cause);
        this.expression = Objects.requireNonNull(expression, "expression");
        if (cursor < 0 || end < cursor || end > expression.length()) {
            throw new IllegalArgumentException("Invalid expression span");
        }
        this.cursor = cursor;
        this.end = end;
    }

    public final String expression() { return expression; }
    public final int cursor() { return cursor; }
    public final int end() { return end; }
    /** Compatibility accessor for the expression text. */
    public final String source() { return expression; }
    /** Compatibility accessor for the inclusive failure start. */
    public final int start() { return cursor; }
}
