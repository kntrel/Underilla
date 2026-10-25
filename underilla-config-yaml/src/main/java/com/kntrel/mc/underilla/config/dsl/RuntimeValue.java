package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.function.Function;

/** Defines a recognized value that is computed from the caller's context during evaluation. */
public interface RuntimeValue<C, T> {

    /** Creates a definition without invoking its pattern or computation. */
    static <C, T> RuntimeValue<C, T> of(TypeToken<T> type, Pattern pattern, Function<C, T> compute) {
        return new RuntimeValueImpl<>(type, pattern, compute);
    }

    /** Convenience overload for result types that can be represented by a class. */
    static <C, T> RuntimeValue<C, T> of(Class<T> type, Pattern pattern, Function<C, T> compute) {
        return of(TypeToken.of(type), pattern, compute);
    }

    /** Recognizes the source text for this already-bound computation. */
    Pattern pattern();

    TypeToken<T> type();

    /**
     * Computes the value using the context supplied for this evaluation. Does not invoke the
     * pattern or cache the result. Context validation and computation are owned by the attached
     * implementation; exceptions propagate to the caller. Context-less definitions may use
     * {@code Void} as their context type and ignore a null context.
     */
    T compute(C context);
}
