package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Objects;
import java.util.function.Function;

record RuntimeValueImpl<C, T>(TypeToken<T> type, Pattern pattern, Function<C, T> evaluator)
        implements RuntimeValue<C> {
    RuntimeValueImpl {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(evaluator, "compute");
    }

    @Override
    public RuntimeValue.Bound<C, T> bind(String matched) {
        Objects.requireNonNull(matched, "matched");
        return new Bound<>(type, evaluator);
    }

    record Bound<C, T>(TypeToken<T> type, Function<C, T> evaluator) implements RuntimeValue.Bound<C, T> {
        Bound {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(evaluator, "evaluator");
        }

        @Override
        public T compute(C context) { return evaluator.apply(context); }
    }
}
