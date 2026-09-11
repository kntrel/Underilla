package com.kntrel.mc.underilla.core.patch;

import java.util.Objects;
import java.util.function.Predicate;

/** Routes a subject to one of two patchers according to a predicate. */
public final class PredicatePatcher<T> implements Patcher<T> {

    private static final Patcher<?> NOOP = _ -> {};

    private final Predicate<T> predicate;
    private final Patcher<T> then;
    private final Patcher<T> otherwise;

    private PredicatePatcher(
            Predicate<T> predicate,
            Patcher<T> then,
            Patcher<T> otherwise
    ) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.then = Objects.requireNonNull(then, "then");
        this.otherwise = Objects.requireNonNull(otherwise, "otherwise");
    }

    public static <T> Builder<T> iff(Predicate<T> predicate) {
        return new Builder<>(predicate);
    }

    @Override
    public void patch(T subject) {
        Objects.requireNonNull(subject, "subject");
        (predicate.test(subject) ? then : otherwise).patch(subject);
    }

    public static final class Builder<T> {

        private final Predicate<T> predicate;
        private Patcher<T> then = noop();
        private Patcher<T> otherwise = noop();

        private Builder(Predicate<T> predicate) {
            this.predicate = Objects.requireNonNull(predicate, "predicate");
        }

        public Builder<T> then(Patcher<T> patcher) {
            this.then = Objects.requireNonNull(patcher, "patcher");
            return this;
        }

        public Builder<T> otherwise(Patcher<T> patcher) {
            this.otherwise = Objects.requireNonNull(patcher, "patcher");
            return this;
        }

        public PredicatePatcher<T> end() {
            return new PredicatePatcher<>(predicate, then, otherwise);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Patcher<T> noop() {
        return (Patcher<T>) NOOP;
    }
}
