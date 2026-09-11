package com.kntrel.mc.underilla.core.patch;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Runs patchers sequentially in their declared order. */
public final class PatcherPipeline<T> implements Patcher<T> {

    @SafeVarargs
    public static <T> PatcherPipeline<T> sequence(Patcher<T>... patchers) {
        return new PatcherPipeline<>(patchers);
    }

    private final List<Patcher<T>> patchers;

    @SafeVarargs
    public PatcherPipeline(Patcher<T>... patchers) {
        this(Arrays.asList(patchers));
    }

    public PatcherPipeline(List<? extends Patcher<T>> patchers) {
        this.patchers = List.copyOf(patchers);
        this.patchers.forEach(patcher -> Objects.requireNonNull(patcher, "patcher"));
    }

    @Override
    public void patch(T subject) {
        patchers.forEach(patcher -> patcher.patch(subject));
    }
}
