package com.kntrel.mc.underilla.config.dsl.exception;

import com.kntrel.mc.underilla.config.dsl.DefinitionSignature;

import java.util.List;

/** Multiple equally specific or incomparable operand definitions match. */
public final class AmbiguousOperandException extends DslCompilationException {
    private static final long serialVersionUID = 1L;
    private final List<DefinitionSignature> candidates;
    public AmbiguousOperandException(String expression, int cursor, int end, List<DefinitionSignature> candidates) {
        super(expression, cursor, end, null);
        this.candidates = List.copyOf(candidates);
    }
    public List<DefinitionSignature> candidates() { return candidates; }
    @Override
    public String getMessage() {
        return diagnostic("Ambiguous operand at " + cursor() + ": '" + offendingText() + "'"
                + "; matching definitions: " + Diagnostics.candidates(candidates));
    }
}
