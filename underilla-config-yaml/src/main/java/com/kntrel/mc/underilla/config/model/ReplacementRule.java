package com.kntrel.mc.underilla.config.model;

import com.kntrel.mc.underilla.core.api.ID;
import java.util.Objects;

/** One ordered replacement entry. {@code condition} maps to YAML's {@code if} key. */
public record ReplacementRule<S>(Selector<S> condition, ID with) {
    public ReplacementRule {
        Objects.requireNonNull(condition, "if");
        Objects.requireNonNull(with, "with");
    }

    public static <S> ReplacementRule<S> of(Selector<S> condition, String with) {
        return new ReplacementRule<>(condition, ID.of(with));
    }
}
