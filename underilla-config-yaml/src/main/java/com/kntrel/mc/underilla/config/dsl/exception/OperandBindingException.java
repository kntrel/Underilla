package com.kntrel.mc.underilla.config.dsl.exception;

import com.kntrel.mc.underilla.config.dsl.DefinitionSignature;

import java.util.Objects;

/** Failed to bind operand; the original callback failure is retained as the cause. */
public final class OperandBindingException extends DslCompilationException {
    private static final long serialVersionUID = 1L;
    private final DefinitionSignature definition;
    public OperandBindingException(String expression, int cursor, int end, DefinitionSignature definition, Throwable cause) {
        super("Failed to bind operand", expression, cursor, end, Objects.requireNonNull(cause, "cause"));
        this.definition = Objects.requireNonNull(definition, "definition");
    }
    public DefinitionSignature definition() { return definition; }
}
