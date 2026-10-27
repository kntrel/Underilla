package com.kntrel.mc.underilla.config.ucel;

import com.kntrel.mc.underilla.config.dsl.Operation;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.entity.Taggable;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.core.api.ID;

/** Tests whether a taggable value contains one tag ID. */
public final class TaggedOperation implements Operation<Taggable, ID, Boolean> {
    private static final Pattern PATTERN = Pattern.keyword("tagged");

    @Override public TypeToken<Taggable> aType() { return TypeToken.of(Taggable.class); }
    @Override public TypeToken<ID> bType() { return TypeToken.of(ID.class); }
    @Override public TypeToken<Boolean> type() { return TypeToken.of(Boolean.class); }
    @Override public Pattern pattern() { return PATTERN; }
    @Override public int precedence() { return 40; }
    @Override public Boolean apply(Taggable lhs, ID rhs) { return lhs.tags().contains(rhs); }

    /** Matches qualified tag operators with one or more whitespace characters between words. */
    static Pattern qualifiedPattern(String qualifier) {
        Pattern word = Pattern.keyword(qualifier);
        return source -> {
            int tagged = PATTERN.match(source);
            if (tagged <= 0) return 0;
            int start = tagged;
            while (start < source.length() && Character.isWhitespace(source.charAt(start))) start++;
            if (start == tagged) return 0;
            int length = word.match(source.subSequence(start, source.length()));
            return length > 0 ? start + length : 0;
        };
    }
}
