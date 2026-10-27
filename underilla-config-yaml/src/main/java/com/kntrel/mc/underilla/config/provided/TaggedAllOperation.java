package com.kntrel.mc.underilla.config.provided;

import com.kntrel.mc.underilla.config.dsl.Operation;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.entity.Taggable;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.Collection;

/** Tests whether every requested tag belongs to the taggable value. */
public final class TaggedAllOperation implements Operation<Taggable, Collection<ID>, Boolean> {
    private static final TypeToken<Collection<ID>> TAGS = new TypeToken<>() {};
    private static final Pattern ALL = TaggedOperation.qualifiedPattern("all");
    private static final Pattern TAGGED = Pattern.keyword("tagged");
    private static final Pattern PATTERN = source -> {
        int qualified = ALL.match(source);
        return qualified > 0 ? qualified : TAGGED.match(source);
    };

    @Override public TypeToken<Taggable> aType() { return TypeToken.of(Taggable.class); }
    @Override public TypeToken<Collection<ID>> bType() { return TAGS; }
    @Override public TypeToken<Boolean> type() { return TypeToken.of(Boolean.class); }
    @Override public Pattern pattern() { return PATTERN; }
    @Override public int precedence() { return 40; }
    @Override public Boolean apply(Taggable lhs, Collection<ID> rhs) { return lhs.tags().containsAll(rhs); }
}
