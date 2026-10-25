package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Internal expression tree evaluated against a caller-supplied context. */
sealed interface Node<C, T> {

    TypeToken<T> type();

    /** Inclusive start offset in the source, measured in UTF-16 code units. */
    int start();

    /** Exclusive end offset in the source, measured in UTF-16 code units. */
    int end();

    /** Returns a copy with a new source span, preserving its computation and children. */
    Node<C, T> withSpan(int start, int end);

    T evaluate(C context);

    private static void checkSpan(int start, int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid source span [" + start + ", " + end + ")");
        }
    }

    record Literal<C, T>(TypeToken<T> type, int start, int end, T value) implements Node<C, T> {

        public Literal {
            Objects.requireNonNull(type, "type");
            checkSpan(start, end);
        }

        @Override
        public Literal<C, T> withSpan(int start, int end) {
            return new Literal<>(type, start, end, value);
        }

        @Override
        public T evaluate(C context) {
            return value;
        }
    }

    record Runtime<C, T>(TypeToken<T> type, int start, int end, Function<C, T> compute) implements Node<C, T> {

        public Runtime {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(compute, "compute");
            checkSpan(start, end);
        }

        @Override
        public Runtime<C, T> withSpan(int start, int end) {
            return new Runtime<>(type, start, end, compute);
        }

        @Override
        public T evaluate(C context) {
            return compute.apply(context);
        }
    }

    /** Evaluates the left operand, then the right operand, using the same context. */
    record Operation<C, A, B, T>(
            TypeToken<T> type,
            int start,
            int end,
            Node<C, A> lhs,
            Node<C, B> rhs,
            BiFunction<A, B, T> compute
    ) implements Node<C, T> {

        public Operation {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(lhs, "lhs");
            Objects.requireNonNull(rhs, "rhs");
            Objects.requireNonNull(compute, "compute");
            checkSpan(start, end);
        }

        @Override
        public Operation<C, A, B, T> withSpan(int start, int end) {
            return new Operation<>(type, start, end, lhs, rhs, compute);
        }

        @Override
        public T evaluate(C context) {
            return compute.apply(lhs.evaluate(context), rhs.evaluate(context));
        }
    }

    record Transformation<C, N, T>(
            TypeToken<T> type,
            int start,
            int end,
            Node<C, N> subject,
            Function<N, T> compute
    ) implements Node<C, T> {

        public Transformation {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(compute, "compute");
            checkSpan(start, end);
        }

        @Override
        public Transformation<C, N, T> withSpan(int start, int end) {
            return new Transformation<>(type, start, end, subject, compute);
        }

        @Override
        public T evaluate(C context) {
            return compute.apply(subject.evaluate(context));
        }
    }
}
