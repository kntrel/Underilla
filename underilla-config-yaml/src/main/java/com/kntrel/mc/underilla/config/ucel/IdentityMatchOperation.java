package com.kntrel.mc.underilla.config.ucel;

import com.kntrel.mc.underilla.config.dsl.Operation;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.core.api.ID;

/** Matches an entire ID string against the regular expression on the right. */
public final class IdentityMatchOperation implements Operation<ID, String, Boolean> {
    private static final Pattern PATTERN = Pattern.keyword("match");

    @Override public TypeToken<ID> aType() { return TypeToken.of(ID.class); }
    @Override public TypeToken<String> bType() { return TypeToken.of(String.class); }
    @Override public TypeToken<Boolean> type() { return TypeToken.of(Boolean.class); }
    @Override public Pattern pattern() { return PATTERN; }
    @Override public int precedence() { return 30; }
    @Override public Boolean apply(ID lhs, String rhs) { return lhs.toString().matches(rhs); }
}
