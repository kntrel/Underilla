package com.kntrel.mc.underilla.config.ucel;

import com.kntrel.mc.underilla.config.dsl.Operation;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.util.TypeToken;

/** Matches an entire string against the regular expression on the right. */
public final class MatchOperation implements Operation<String, String, Boolean> {
    private static final Pattern PATTERN = Pattern.keyword("match");

    @Override public TypeToken<String> aType() { return TypeToken.of(String.class); }
    @Override public TypeToken<String> bType() { return TypeToken.of(String.class); }
    @Override public TypeToken<Boolean> type() { return TypeToken.of(Boolean.class); }
    @Override public Pattern pattern() { return PATTERN; }
    @Override public int precedence() { return 30; }
    @Override public Boolean apply(String lhs, String rhs) { return lhs.matches(rhs); }
}
