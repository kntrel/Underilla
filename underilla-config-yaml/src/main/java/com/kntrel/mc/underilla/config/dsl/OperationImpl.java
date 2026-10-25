package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Objects;
import java.util.function.BiFunction;

record OperationImpl<A, B, R>(
        TypeToken<A> aType,
        TypeToken<B> bType,
        TypeToken<R> type,
        Pattern pattern,
        int precedence,
        BiFunction<A, B, R> compute
) implements Operation<A, B, R> {

    OperationImpl {
        Objects.requireNonNull(aType, "aType");
        Objects.requireNonNull(bType, "bType");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(compute, "compute");
    }

    @Override
    public R apply(A a, B b) {
        return compute.apply(a, b);
    }
}
