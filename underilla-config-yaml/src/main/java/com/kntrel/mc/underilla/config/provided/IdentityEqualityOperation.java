package com.kntrel.mc.underilla.config.provided;

import com.kntrel.mc.underilla.config.dsl.Operation;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.entity.Identifiable;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.core.api.ID;

/** Compares an identifiable value's ID with another ID. */
public final class IdentityEqualityOperation implements Operation<Identifiable, ID, Boolean> {
    private final boolean negated;
    private final Pattern pattern;

    /** Creates the {@code =} operation. */
    public IdentityEqualityOperation() {
        this(false);
    }

    /** Creates {@code !=} when negated, or {@code =} otherwise. */
    public IdentityEqualityOperation(boolean negated) {
        this.negated = negated;
        this.pattern = Pattern.literal(negated ? "!=" : "=");
    }

    @Override public TypeToken<Identifiable> aType() { return TypeToken.of(Identifiable.class); }
    @Override public TypeToken<ID> bType() { return TypeToken.of(ID.class); }
    @Override public TypeToken<Boolean> type() { return TypeToken.of(Boolean.class); }
    @Override public Pattern pattern() { return pattern; }
    @Override public int precedence() { return 30; }
    @Override public Boolean apply(Identifiable lhs, ID rhs) { return lhs.id().equals(rhs) != negated; }
}
