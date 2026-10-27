package com.kntrel.mc.underilla.config.dsl.exception;

import java.util.Objects;

/** A checked compilation failure with a half-open UTF-16 source span. */
public abstract class DslCompilationException extends Exception {
    private static final long serialVersionUID = 1L;
    private final String expression;
    private final int cursor;
    private final int end;

    protected DslCompilationException(String expression, int cursor, int end, Throwable cause) {
        super(null, cause);
        this.expression = Objects.requireNonNull(expression, "expression");
        if (cursor < 0 || end < cursor || end > expression.length()) {
            throw new IllegalArgumentException("Invalid expression span");
        }
        this.cursor = cursor;
        this.end = end;
    }

    /** Describes this failure with the relevant source line and a caret; never logs. */
    @Override
    public abstract String getMessage();

    protected final String diagnostic(String description) {
        return Diagnostics.render(description, expression, cursor, end);
    }

    protected final String offendingText() {
        return Diagnostics.token(expression, cursor, end);
    }

    public final String expression() { return expression; }
    public final int cursor() { return cursor; }
    public final int end() { return end; }
    /** Compatibility accessor for the expression text. */
    public final String source() { return expression; }
    /** Compatibility accessor for the inclusive failure start. */
    public final int start() { return cursor; }
}
