package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Objects;
import java.util.function.Function;

record TransformationImpl<A, R>(
        TypeToken<A> inputType,
        TypeToken<R> type,
        Pattern pattern,
        Function<A, R> compute
) implements Transformation<A, R> {

    TransformationImpl {
        Objects.requireNonNull(inputType, "inputType");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(compute, "compute");
    }

    @Override
    public R apply(A subject) {
        return compute.apply(subject);
    }
}
