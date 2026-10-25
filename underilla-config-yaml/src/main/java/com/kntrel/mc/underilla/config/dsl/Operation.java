package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.function.BiFunction;

/**
 * Defines a binary operation in source order {@code {a} {op} {b}}. Operations of equal
 * precedence group from the left: {@code a + b + c} means {@code (a + b) + c}.
 */
public interface Operation<A, B, R> extends BiFunction<A, B, R> {

    /**
     * Creates a left-associative infix operation with precedence zero. Construction does not
     * invoke the pattern or computation.
     */
    static <A, B, R> Operation<A, B, R> of(
            TypeToken<A> aType,
            TypeToken<B> bType,
            TypeToken<R> type,
            Pattern pattern,
            BiFunction<A, B, R> compute
    ) {
        return of(aType, bType, type, pattern, 0, compute);
    }

    /** Convenience overload for types that can be represented by classes. */
    static <A, B, R> Operation<A, B, R> of(
            Class<A> aType,
            Class<B> bType,
            Class<R> type,
            Pattern pattern,
            BiFunction<A, B, R> compute
    ) {
        return of(TypeToken.of(aType), TypeToken.of(bType), TypeToken.of(type), pattern, compute);
    }

    /** Creates an operation with explicit precedence. */
    static <A, B, R> Operation<A, B, R> of(
            TypeToken<A> aType,
            TypeToken<B> bType,
            TypeToken<R> type,
            Pattern pattern,
            int precedence,
            BiFunction<A, B, R> compute) {
        return new OperationImpl<>(aType, bType, type, pattern, precedence, compute);
    }

    /** Convenience overload for explicit precedence and types represented by classes. */
    static <A, B, R> Operation<A, B, R> of(
            Class<A> aType,
            Class<B> bType,
            Class<R> type,
            Pattern pattern,
            int precedence,
            BiFunction<A, B, R> compute
    ) {
        return of(TypeToken.of(aType), TypeToken.of(bType), TypeToken.of(type),
                pattern, precedence, compute);
    }

    /** Type of the expression to the left of the operator. */
    TypeToken<A> aType();

    /** Type of the expression to the right of the operator. */
    TypeToken<B> bType();

    /** The operation's result type. */
    TypeToken<R> type();

    /** Recognizes only the operator text; operand expressions are parsed separately. */
    Pattern pattern();

    /** Higher values bind more tightly; operations of equal precedence group from the left. */
    int precedence();

    /**
     * Computes from already evaluated operands in left-to-right source order. Does not invoke
     * the pattern. Computation exceptions propagate to the caller.
     */
    @Override
    R apply(A a, B b);
}
