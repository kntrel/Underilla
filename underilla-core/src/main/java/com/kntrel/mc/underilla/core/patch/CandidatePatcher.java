package com.kntrel.mc.underilla.core.patch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Patches a speculative candidate, then handles either the accepted or rejected attempt. */
public final class CandidatePatcher<T> implements Patcher<T> {

    private final Function<T, T> take;
    private final Patcher<T> patcher;
    private final BiPredicate<T, T> test;
    private final BiConsumer<T, T> then;
    private final BiConsumer<T, T> otherwise;

    private CandidatePatcher(
            Function<T, T> take,
            Patcher<T> patcher,
            BiPredicate<T, T> test,
            BiConsumer<T, T> then,
            BiConsumer<T, T> otherwise
    ) {
        this.take = Objects.requireNonNull(take, "take");
        this.patcher = Objects.requireNonNull(patcher, "patcher");
        this.test = Objects.requireNonNull(test, "test");
        this.then = Objects.requireNonNull(then, "then");
        this.otherwise = Objects.requireNonNull(otherwise, "otherwise");
    }

    public static <T> Builder<T> take(Function<T, T> take) {
        return new Builder<>(take);
    }

    @Override
    public void patch(T original) {
        Objects.requireNonNull(original, "original");
        T taken = Objects.requireNonNull(take.apply(original), "take returned null");
        if (taken == original) {
            throw new IllegalStateException("take must return a distinct candidate");
        }
        patcher.patch(taken);
        (test.test(original, taken) ? then : otherwise).accept(original, taken);
    }

    public static final class Builder<T> {

        private final List<Patcher<T>> patchers = new ArrayList<>();
        private final Function<T, T> take;
        private BiPredicate<T, T> test = (_, _) -> true;
        private BiConsumer<T, T> then = (_, _) -> {};
        private BiConsumer<T, T> otherwise = (_, _) -> {};

        private Builder(Function<T, T> take) {
            this.take = Objects.requireNonNull(take, "take");
        }

        @SafeVarargs
        public final Builder<T> patch(Patcher<T>... patchers) {
            Objects.requireNonNull(patchers, "patchers");
            for (Patcher<T> patcher : patchers) {
                this.patchers.add(Objects.requireNonNull(patcher, "patcher"));
            }
            return this;
        }

        public final Builder<T> patch(Collection<? extends Patcher<T>> patchers) {
            Objects.requireNonNull(patchers, "patchers");
            for (Patcher<T> patcher : patchers) {
                this.patchers.add(Objects.requireNonNull(patcher, "patcher"));
            }
            return this;
        }

        public Builder<T> iff(BiPredicate<T, T> test) {
            this.test = Objects.requireNonNull(test, "test");
            return this;
        }

        public Builder<T> then(BiConsumer<T, T> then) {
            this.then = Objects.requireNonNull(then, "then");
            return this;
        }

        public Builder<T> otherwise(BiConsumer<T, T> otherwise) {
            this.otherwise = Objects.requireNonNull(otherwise, "otherwise");
            return this;
        }

        public CandidatePatcher<T> end() {
            return new CandidatePatcher<>(
                    take,
                    new PatcherPipeline<>(patchers),
                    test,
                    then,
                    otherwise
            );
        }
    }
}
