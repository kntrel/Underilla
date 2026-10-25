package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Objects;
import java.util.function.Function;

record RuntimeValueImpl<C, T>(
        TypeToken<T> type,
        Pattern pattern,
        Function<C, T> evaluator
) implements RuntimeValue<C, T> {

    RuntimeValueImpl {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(evaluator, "compute");
    }

    @Override
    public T compute(C context) {
        return evaluator.apply(context);
    }
}
