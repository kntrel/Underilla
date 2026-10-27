package com.kntrel.mc.underilla.config.ucel;

import com.kntrel.mc.underilla.config.dsl.Operation;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.entity.Taggable;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.Collection;

/** Tests whether at least one requested tag belongs to the taggable value. */
final class TaggedAnyOperation implements Operation<Taggable, Collection<ID>, Boolean> {
    private static final TypeToken<Collection<ID>> TAGS = new TypeToken<>() {};
    private static final Pattern PATTERN = TaggedOperation.qualifiedPattern("any");

    @Override public TypeToken<Taggable> aType() { return TypeToken.of(Taggable.class); }
    @Override public TypeToken<Collection<ID>> bType() { return TAGS; }
    @Override public TypeToken<Boolean> type() { return TypeToken.of(Boolean.class); }
    @Override public Pattern pattern() { return PATTERN; }
    @Override public int precedence() { return 40; }

    @Override
    public Boolean apply(Taggable lhs, Collection<ID> rhs) {
        var tags = lhs.tags();
        for (ID requested : rhs) {
            if (tags.contains(requested)) return true;
        }
        return false;
    }
}
