package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.function.Function;

/** A typed prefix computation applied to the following operand or parenthesized expression. */
public interface Transformation<A, R> extends Function<A, R> {

    static <A, R> Transformation<A, R> of(
            TypeToken<A> inputType, TypeToken<R> type, Pattern pattern, Function<A, R> compute) {
        return new TransformationImpl<>(inputType, type, pattern, compute);
    }

    static <A, R> Transformation<A, R> of(
            Class<A> inputType, Class<R> type, Pattern pattern, Function<A, R> compute) {
        return of(TypeToken.of(inputType), TypeToken.of(type), pattern, compute);
    }

    TypeToken<A> inputType();

    TypeToken<R> type();

    /** Matches the prefix only; its subject is parsed separately and binds before binary operations. */
    Pattern pattern();

    @Override
    R apply(A subject);
}
