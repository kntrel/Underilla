package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.function.Function;

/** Matches runtime text and binds it to a typed computation without evaluating the context. */
public interface RuntimeValue<C> {
    /** Creates a definition with a fixed result type and context computation. */
    static <C, T> RuntimeValue<C> of(TypeToken<T> type, Pattern pattern, Function<C, T> compute) {
        return new RuntimeValueImpl<>(type, pattern, compute);
    }

    static <C, T> RuntimeValue<C> of(Class<T> type, Pattern pattern, Function<C, T> compute) {
        return of(TypeToken.of(type), pattern, compute);
    }

    Pattern pattern();

    /** Resolves the matched text's result type and computation during compilation. */
    Bound<C, ?> bind(String matched);

    /** A bound value reads the actual context only when compute is called. */
    interface Bound<C, T> {
        static <C, T> Bound<C, T> of(TypeToken<T> type, Function<C, T> evaluator) {
            return new RuntimeValueImpl.Bound<>(type, evaluator);
        }

        TypeToken<T> type();

        T compute(C context);
    }
}
