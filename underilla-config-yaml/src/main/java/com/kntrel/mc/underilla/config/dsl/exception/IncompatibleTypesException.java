package com.kntrel.mc.underilla.config.dsl.exception;

import com.kntrel.mc.underilla.config.dsl.DefinitionSignature;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.List;

/** A result or operand list cannot satisfy any expected type signature. */
public final class IncompatibleTypesException extends DslCompilationException {
    private static final long serialVersionUID = 1L;
    private final List<TypeToken<?>> actualTypes;
    private final List<List<TypeToken<?>>> expectedTypes;
    private final List<DefinitionSignature> candidates;

    public IncompatibleTypesException(String expression, int cursor, int end, List<TypeToken<?>> actualTypes,
            List<List<TypeToken<?>>> expectedTypes, List<DefinitionSignature> candidates) {
        super(expression, cursor, end, null);
        this.actualTypes = List.copyOf(actualTypes);
        this.expectedTypes = expectedTypes.stream().map(List::copyOf).toList();
        this.candidates = List.copyOf(candidates);
    }
    public List<TypeToken<?>> actualTypes() { return actualTypes; }
    public List<List<TypeToken<?>>> expectedTypes() { return expectedTypes; }
    public List<DefinitionSignature> candidates() { return candidates; }
    @Override
    public String getMessage() {
        String expected = expectedTypes.stream().map(Diagnostics::types).collect(java.util.stream.Collectors.joining(" or "));
        String subject = candidates.isEmpty() ? "result" : candidates.getFirst().kind().name().toLowerCase(java.util.Locale.ROOT)
                + " '" + offendingText() + "'";
        return diagnostic("Incompatible types for " + subject + " at " + cursor() + ": found "
                + Diagnostics.types(actualTypes) + "; expected " + expected);
    }
}
