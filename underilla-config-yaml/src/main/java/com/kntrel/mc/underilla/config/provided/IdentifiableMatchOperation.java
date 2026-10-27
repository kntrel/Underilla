package com.kntrel.mc.underilla.config.provided;

import com.kntrel.mc.underilla.config.dsl.Operation;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.entity.Identifiable;
import com.kntrel.mc.underilla.config.util.TypeToken;

/** Matches an identifiable value's entire ID string against the regular expression on the right. */
public final class IdentifiableMatchOperation implements Operation<Identifiable, String, Boolean> {
    private static final Pattern PATTERN = Pattern.keyword("match");

    @Override public TypeToken<Identifiable> aType() { return TypeToken.of(Identifiable.class); }
    @Override public TypeToken<String> bType() { return TypeToken.of(String.class); }
    @Override public TypeToken<Boolean> type() { return TypeToken.of(Boolean.class); }
    @Override public Pattern pattern() { return PATTERN; }
    @Override public int precedence() { return 30; }
    @Override public Boolean apply(Identifiable lhs, String rhs) { return lhs.id().toString().matches(rhs); }
}
