package com.kntrel.mc.underilla.config.dsl.exception;

import com.kntrel.mc.underilla.config.dsl.DefinitionSignature;

import java.util.Objects;

/** Pattern matching failed; the original callback failure is retained as the cause. */
public final class PatternEvaluationException extends DslCompilationException {
    private static final long serialVersionUID = 1L;
    private final DefinitionSignature definition;
    public PatternEvaluationException(String expression, int cursor, int end, DefinitionSignature definition, Throwable cause) {
        super(expression, cursor, end, Objects.requireNonNull(cause, "cause"));
        this.definition = Objects.requireNonNull(definition, "definition");
    }
    public DefinitionSignature definition() { return definition; }
    @Override
    public String getMessage() {
        return diagnostic("Pattern matching failed at " + cursor() + " for " + Diagnostics.signature(definition)
                + ": " + Diagnostics.cause(getCause()));
    }
}
