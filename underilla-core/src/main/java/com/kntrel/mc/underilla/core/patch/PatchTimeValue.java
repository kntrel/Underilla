package com.kntrel.mc.underilla.core.patch;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Calculates and owns one scoped value for each enclosing {@link PatcherPipeline} invocation. */
public final class PatchTimeValue<T, V> implements Supplier<V> {

    private final Function<? super T, ? extends V> calculation;
    private final ThreadLocal<Deque<V>> values = ThreadLocal.withInitial(ArrayDeque::new);

    private PatchTimeValue(Function<? super T, ? extends V> calculation) {
        this.calculation = Objects.requireNonNull(calculation, "calculation");
    }

    public static <T, V> PatchTimeValue<T, V> from(Function<? super T, ? extends V> calculation) {
        return new PatchTimeValue<>(calculation);
    }

    /** Returns the value calculated for the current pipeline invocation. */
    @Override
    public V get() {
        Deque<V> current = values.get();
        if (current.isEmpty()) {
            values.remove();
            throw new NoSuchElementException("patch-time value is not in scope");
        }
        return current.peek();
    }

    void bind(T subject) {
        values.get().push(Objects.requireNonNull(calculation.apply(subject), "calculation returned null"));
    }

    void unbind() {
        Deque<V> current = values.get();
        current.pop();
        if (current.isEmpty()) {
            values.remove();
        }
    }
}
