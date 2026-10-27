package com.kntrel.mc.underilla.config.model;

import com.kntrel.mc.underilla.config.dsl.Compiler;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** An executable selection rule over a compiler's scoped context. */
@FunctionalInterface
public interface Selector<C> extends Predicate<C> {
    static <C> Selector<C> of(Predicate<? super C> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return predicate::test;
    }

    static <C> Selector<C> of(boolean value) { return _ -> value; }

    static <C> Selector<C> all() { return of(true); }

    static <C> Selector<C> none() { return of(false); }

    /** Compiles immediately; the selector retains only the executable expression. */
    static <C> Selector<C> compile(Compiler<C> compiler, String expression) {
        var compiled = Objects.requireNonNull(compiler, "compiler").compile(expression, Boolean.class);
        return compiled::apply;
    }

    /** Copies the predicates in order and evaluates them using short-circuit OR. An empty list is false. */
    static <C> Selector<C> anyOf(Collection<? extends Predicate<? super C>> predicates) {
        var copied = List.copyOf(predicates);
        return context -> {
            for (var predicate : copied) {
                if (predicate.test(context)) return true;
            }
            return false;
        };
    }

    @Override
    default Selector<C> and(Predicate<? super C> other) { return of(Predicate.super.and(other)); }

    @Override
    default Selector<C> or(Predicate<? super C> other) { return of(Predicate.super.or(other)); }

    @Override
    default Selector<C> negate() { return of(Predicate.super.negate()); }
}
