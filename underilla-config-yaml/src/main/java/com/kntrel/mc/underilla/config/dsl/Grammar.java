package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Language definitions and runtime values for an evaluation context. Lists are immutable
 * snapshots that preserve registration order; definitions are retained by reference.
 */
public final class Grammar<C> {

    private final List<TypeLiteral<?>> literals;
    private final List<Operation<?, ?, ?>> operations;
    private final List<Transformation<?, ?>> transformations;
    private final List<RuntimeValue<C>> runtimeValues;

    /** Copies the collections in iteration order and rejects null collections or definitions. */
    public Grammar(
            Collection<? extends TypeLiteral<?>> literals,
            Collection<? extends Operation<?, ?, ?>> operations,
            Collection<? extends RuntimeValue<C>> runtimeValues
    ) {
        this(literals, operations, List.of(), runtimeValues);
    }

    /** Copies all definitions in iteration order and rejects null collections or definitions. */
    public Grammar(
            Collection<? extends TypeLiteral<?>> literals,
            Collection<? extends Operation<?, ?, ?>> operations,
            Collection<? extends Transformation<?, ?>> transformations,
            Collection<? extends RuntimeValue<C>> runtimeValues
    ) {

        this.literals = List.copyOf(literals);
        this.operations = List.copyOf(operations);
        this.transformations = List.copyOf(transformations);
        this.runtimeValues = List.copyOf(runtimeValues);
    }

    /** Creates a grammar without runtime values. */
    public Grammar(
            Collection<? extends TypeLiteral<?>> literals,
            Collection<? extends Operation<?, ?, ?>> operations
    ) {
        this(literals, operations, List.of());
    }

    public List<TypeLiteral<?>> literals() {
        return literals;
    }

    public List<Operation<?, ?, ?>> operations() {
        return operations;
    }

    public List<RuntimeValue<C>> runtimeValues() {
        return runtimeValues;
    }

    public List<Transformation<?, ?>> transformations() {
        return transformations;
    }

    /** Starts an empty builder. The type token establishes the builder's context type. */
    public static <C> Builder<C> with(TypeToken<C> contextType) {
        Objects.requireNonNull(contextType, "contextType");
        return new Builder<>();
    }

    public static <C> Builder<C> with(Class<C> contextType) {
        return with(TypeToken.of(contextType));
    }

    /**
     * Creates a grammar for another context using the same literal and operation definitions.
     * The supplied runtime values replace this grammar's runtime values and are copied in
     * iteration order. This grammar is unchanged.
     */
    public <D> Grammar<D> withRuntimeValues(Collection<? extends RuntimeValue<D>> runtimeValues) {
        return new Grammar<>(literals, operations, transformations, runtimeValues);
    }

    /** Creates a context-less grammar using the same literals and operations. */
    public Grammar<Void> withoutRuntimeValues() {
        return new Grammar<>(literals, operations, transformations, List.of());
    }

    /** Mutable registration builder; each call to {@link #done()} produces a separate snapshot. */
    public static final class Builder<C> {

        private final List<TypeLiteral<?>> literals = new ArrayList<>();
        private final List<Operation<?, ?, ?>> operations = new ArrayList<>();
        private final List<Transformation<?, ?>> transformations = new ArrayList<>();
        private final List<RuntimeValue<C>> runtimeValues = new ArrayList<>();
        private boolean standardAdded;

        private Builder() {}

        public Builder<C> literal(TypeLiteral<?> literal) {
            literals.add(Objects.requireNonNull(literal, "literal"));
            return this;
        }

        public <T> Builder<C> literal(TypeToken<T> type, Pattern pattern, Function<String, T> parse) {
            return literal(TypeLiteral.of(type, pattern, parse));
        }

        public <T> Builder<C> literal(Class<T> type, Pattern pattern, Function<String, T> parse) {
            return literal(TypeLiteral.of(type, pattern, parse));
        }

        public Builder<C> operation(Operation<?, ?, ?> operation) {
            operations.add(Objects.requireNonNull(operation, "operation"));
            return this;
        }

        /** Registers a left-associative operation with precedence zero. */
        public <A, B, R> Builder<C> operation(TypeToken<A> aType, TypeToken<B> bType, TypeToken<R> type, Pattern pattern, BiFunction<A, B, R> compute) {
            return operation(Operation.of(aType, bType, type, pattern, compute));
        }

        /** Registers a left-associative operation with precedence zero. */
        public <A, B, R> Builder<C> operation(Class<A> aType, Class<B> bType, Class<R> type, Pattern pattern, BiFunction<A, B, R> compute) {
            return operation(Operation.of(aType, bType, type, pattern, compute));
        }

        public <A, B, R> Builder<C> operation(TypeToken<A> aType, TypeToken<B> bType, TypeToken<R> type, Pattern pattern, int precedence, BiFunction<A, B, R> compute) {
            return operation(Operation.of(aType, bType, type, pattern, precedence, compute));
        }

        public <A, B, R> Builder<C> operation(Class<A> aType, Class<B> bType, Class<R> type, Pattern pattern, int precedence, BiFunction<A, B, R> compute) {
            return operation(Operation.of(aType, bType, type, pattern, precedence, compute));
        }

        public Builder<C> transformation(Transformation<?, ?> transformation) {
            transformations.add(Objects.requireNonNull(transformation, "transformation"));
            return this;
        }

        public <A, R> Builder<C> transformation(
                TypeToken<A> inputType, TypeToken<R> type, Pattern pattern, Function<A, R> compute) {
            return transformation(Transformation.of(inputType, type, pattern, compute));
        }

        public <A, R> Builder<C> transformation(Class<A> inputType, Class<R> type, Pattern pattern, Function<A, R> compute) {
            return transformation(Transformation.of(inputType, type, pattern, compute));
        }

        public Builder<C> runtime(RuntimeValue<C> runtimeValue) {
            runtimeValues.add(Objects.requireNonNull(runtimeValue, "runtimeValue"));
            return this;
        }

        public <T> Builder<C> runtime(TypeToken<T> type, Pattern pattern, Function<C, T> compute) {
            return runtime(RuntimeValue.of(type, pattern, compute));
        }

        public <T> Builder<C> runtime(Class<T> type, Pattern pattern, Function<C, T> compute) {
            return runtime(RuntimeValue.of(type, pattern, compute));
        }

        /**
         * Adds standard literals, arithmetic, comparisons, equality ({@code =} and {@code !=}), membership, boolean
         * operations, and boolean negation ({@code not} or {@code !}). Repeated calls add the defaults only once.
         * Custom definitions are retained; conflicting definitions remain compilation errors.
         *
         * <p>Unsuffixed integers have type Integer and decimal/exponent literals Double.
         * Suffixes B, S, L, F, D (case-insensitive) select Byte, Short, Long, Float, Double.
         * Arithmetic promotes Byte and Short to Integer, then Integer to Long to Float to
         * Double. Integral division truncates. Statically broader Number operands use double
         * arithmetic. Boolean operations are eager.
         */
        public Builder<C> standard() {
            if (!standardAdded) {
                literals.addAll(GrammarStd.LITERALS);
                operations.addAll(GrammarStd.OPERATIONS);
                transformations.addAll(GrammarStd.TRANSFORMATIONS);
                standardAdded = true;
            }
            return this;
        }

        public Grammar<C> done() {
            return new Grammar<>(literals, operations, transformations, runtimeValues);
        }
    }
}
