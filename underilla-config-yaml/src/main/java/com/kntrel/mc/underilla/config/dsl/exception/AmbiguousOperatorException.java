package com.kntrel.mc.underilla.config.dsl.exception;

import com.kntrel.mc.underilla.config.dsl.DefinitionSignature;

import java.util.List;

/** Multiple equally specific or incomparable operator definitions match. */
public final class AmbiguousOperatorException extends DslCompilationException {
    private static final long serialVersionUID = 1L;
    private final List<DefinitionSignature> candidates;
    public AmbiguousOperatorException(String expression, int cursor, int end, List<DefinitionSignature> candidates) {
        super("Ambiguous operator", expression, cursor, end, null);
        this.candidates = List.copyOf(candidates);
    }
    public List<DefinitionSignature> candidates() { return candidates; }
}
