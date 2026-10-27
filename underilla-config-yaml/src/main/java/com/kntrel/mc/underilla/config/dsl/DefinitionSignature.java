package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.List;
import java.util.Objects;

/** Captured definition metadata; resultType is null for a runtime value whose type is resolved by binding. */
public record DefinitionSignature(Kind kind, Pattern pattern, List<TypeToken<?>> operandTypes, TypeToken<?> resultType) {
    public enum Kind { LITERAL, RUNTIME_VALUE, OPERATOR, TRANSFORMATION }
    public DefinitionSignature {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(pattern, "pattern");
        operandTypes = List.copyOf(operandTypes);
    }
}
