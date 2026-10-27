package com.kntrel.mc.underilla.config.dsl.exception;

import com.kntrel.mc.underilla.config.dsl.DefinitionSignature;

import java.util.List;
import com.kntrel.mc.underilla.config.util.TypeToken;

/** Multiple equally specific or incomparable transformation definitions match. */
public final class AmbiguousTransformationException extends DslCompilationException {
    private static final long serialVersionUID = 1L;
    private final List<DefinitionSignature> candidates;
    private final List<TypeToken<?>> actualTypes;
    public AmbiguousTransformationException(String expression, int cursor, int end, List<TypeToken<?>> actualTypes, List<DefinitionSignature> candidates) {
        super(expression, cursor, end, null);
        this.candidates = List.copyOf(candidates);
        this.actualTypes = List.copyOf(actualTypes);
    }
    public List<TypeToken<?>> actualTypes() { return actualTypes; }
    public List<DefinitionSignature> candidates() { return candidates; }
    @Override
    public String getMessage() {
        return diagnostic("Ambiguous transformation at " + cursor() + ": '" + offendingText() + "'" + " for " + Diagnostics.types(actualTypes)
                + "; matching definitions: " + Diagnostics.candidates(candidates));
    }
}
