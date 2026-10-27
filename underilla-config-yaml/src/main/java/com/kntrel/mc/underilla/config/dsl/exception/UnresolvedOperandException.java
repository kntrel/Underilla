package com.kntrel.mc.underilla.config.dsl.exception;

/** No registered operand or prefix transformation recognizes the input. */
public final class UnresolvedOperandException extends DslCompilationException {
    private static final long serialVersionUID = 1L;
    public UnresolvedOperandException(String expression, int cursor, int end) {
        super("Unrecognized operand", expression, cursor, end, null);
    }
    public String operand() { return expression().substring(cursor(), end()); }
}
