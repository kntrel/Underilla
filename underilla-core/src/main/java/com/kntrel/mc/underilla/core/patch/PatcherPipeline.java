package com.kntrel.mc.underilla.core.patch;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Runs patchers sequentially in their declared order. */
public final class PatcherPipeline<T> implements Patcher<T> {

    @SafeVarargs
    public static <T> PatcherPipeline<T> sequence(Patcher<T>... patchers) {
        return new PatcherPipeline<>(patchers);
    }

    @SafeVarargs
    public static <T> Builder<T> with(PatchTimeValue<T, ?>... values) {
        return new Builder<>(List.of(values));
    }

    private final List<PatchTimeValue<T, ?>> values;
    private final List<Patcher<T>> patchers;

    @SafeVarargs
    public PatcherPipeline(Patcher<T>... patchers) {
        this(Arrays.asList(patchers));
    }

    public PatcherPipeline(List<? extends Patcher<T>> patchers) {
        this(List.of(), patchers);
    }

    private PatcherPipeline(
            Collection<? extends PatchTimeValue<T, ?>> values,
            Collection<? extends Patcher<T>> patchers
    ) {
        this.values = List.copyOf(values);
        this.values.forEach(value -> Objects.requireNonNull(value, "value"));
        this.patchers = List.copyOf(patchers);
        this.patchers.forEach(patcher -> Objects.requireNonNull(patcher, "patcher"));
    }

    @Override
    public void patch(T subject) {
        if (values.isEmpty()) {
            patchers.forEach(patcher -> patcher.patch(subject));
            return;
        }
        int boundValues = 0;
        try {
            for (PatchTimeValue<T, ?> value : values) {
                value.bind(subject);
                boundValues++;
            }
            patchers.forEach(patcher -> patcher.patch(subject));
        } finally {
            for (int index = boundValues - 1; index >= 0; index--) {
                values.get(index).unbind();
            }
        }
    }

    public static final class Builder<T> {

        private final List<PatchTimeValue<T, ?>> values;

        private Builder(List<PatchTimeValue<T, ?>> values) {
            this.values = List.copyOf(values);
        }

        @SafeVarargs
        public final PatcherPipeline<T> sequence(Patcher<T>... patchers) {
            return sequence(Arrays.asList(patchers));
        }

        public PatcherPipeline<T> sequence(Collection<? extends Patcher<T>> patchers) {
            return new PatcherPipeline<>(values, patchers);
        }
    }
}
