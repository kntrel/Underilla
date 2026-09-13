package com.kntrel.mc.underilla.core.patch;

import java.util.function.Function;
import java.util.function.Predicate;

/** Applies one operation to a subject. */
@FunctionalInterface
public interface Patcher<T> {

    static <T> CandidatePatcher.Builder<T> take(Function<T, T> take) {
        return CandidatePatcher.take(take);
    }

    static <T> PredicatePatcher.Builder<T> iff(Predicate<T> predicate) {
        return PredicatePatcher.iff(predicate);
    }

    @SafeVarargs
    static <T> PatcherPipeline<T> sequence(Patcher<T>... patchers) {
        return PatcherPipeline.sequence(patchers);
    }

    @SafeVarargs
    static <T> PatcherPipeline.Builder<T> with(PatchTimeValue<T, ?>... values) {
        return PatcherPipeline.with(values);
    }

    static <T, V> PatchTimeValue<T, V> value(Function<? super T, ? extends V> calculation) {
        return PatchTimeValue.from(calculation);
    }

    void patch(T subject);
}
