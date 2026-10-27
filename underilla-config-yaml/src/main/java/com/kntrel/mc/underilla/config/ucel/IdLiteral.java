package com.kntrel.mc.underilla.config.ucel;

import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.dsl.TypeLiteral;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.core.api.ID;

/** A resource ID literal in {@code @namespace:value} form. */
public final class IdLiteral implements TypeLiteral<ID> {
    private static final Pattern PATTERN = Pattern.regex("@[a-z0-9._-]+:[a-z0-9/._-]+");

    @Override public TypeToken<ID> type() { return TypeToken.of(ID.class); }
    @Override public Pattern pattern() { return PATTERN; }
    @Override public ID parse(String matched) { return ID.of(matched.substring(1)); }
}
