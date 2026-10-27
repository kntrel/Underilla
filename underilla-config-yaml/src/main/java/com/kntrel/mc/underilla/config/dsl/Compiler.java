package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Compiles expressions to context-to-result functions. Parentheses provide grouping;
 * prefix transformations bind before binary operations. Higher precedence binds more tightly
 * and equal precedence groups from the left. Patterns
 * match at the current cursor, with longest matches winning. Equal-length operand matches
 * are ambiguous; operator overloads are selected by their declared operand types and must
 * agree on precedence for the matched text. The most specific compatible overload wins;
 * equally specific or incomparable overloads are ambiguous.
 *
 * <p>Operands and results support declared-type assignability, including generic supertypes
 * and wildcard bounds. Primitive and boxed types are equivalent. No numeric promotion or
 * inference of unresolved type variables is performed. Literals
 * are parsed once during compilation; runtime values and operations run during evaluation.
 *
 * <p>Each compilation has independent parser state. Definition metadata is captured at
 * construction; supplied patterns, parsers, and computations retain their own thread-safety
 * semantics. Parentheses at token boundaries are reserved for grouping; square brackets
 * and commas delimit list expressions. Nonempty lists infer their element type from the
 * nearest common superclass of their declared element types, boxing primitives first.
 * Empty list element types are inferred from siblings or the selected consumer, defaulting
 * to Object when the consumer provides no element type. Generic assignability stays invariant.
 */
public final class Compiler<C> {

    private final List<OperandDefinition<C>> operands;
    private final List<OperatorDefinition<?, ?, ?>> operators;
    private final List<TransformationDefinition<?, ?>> transformations;

    public Compiler(Grammar<C> grammar) {
        Objects.requireNonNull(grammar, "grammar");
        List<OperandDefinition<C>> operands = new ArrayList<>();
        for (TypeLiteral<?> literal : grammar.literals()) {
            operands.add(literalDefinition(literal));
        }
        for (RuntimeValue<C> runtimeValue : grammar.runtimeValues()) {
            operands.add(runtimeDefinition(runtimeValue));
        }
        this.operands = List.copyOf(operands);
        List<OperatorDefinition<?, ?, ?>> operators = new ArrayList<>();
        for (Operation<?, ?, ?> operation : grammar.operations()) {
            operators.add(operatorDefinition(operation));
        }
        this.operators = List.copyOf(operators);
        List<TransformationDefinition<?, ?>> transformations = new ArrayList<>();
        for (Transformation<?, ?> transformation : grammar.transformations()) {
            transformations.add(transformationDefinition(transformation));
        }
        this.transformations = List.copyOf(transformations);
    }

    public static <C> Compiler<C> of(Grammar<C> grammar) {
        return new Compiler<>(grammar);
    }

    /** Compiles the entire source and verifies its declared result type before returning. */
    public <T> Function<C, T> compile(String source, TypeToken<T> expectedType) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(expectedType, "expectedType");
        Session session = new Session(source);
        Node<C, ?> root = session.expression(Long.MIN_VALUE);
        session.skipWhitespace();
        if (session.cursor != source.length()) {
            throw session.error("Unexpected input; expected an operator or end of expression",
                    session.cursor, session.cursor + 1, null);
        }
        Node<C, ?> resolved = specialize(root, expectedType);
        if (resolved == null) {
            throw session.error("Expected result type " + expectedType + " but found " + root.type(),
                    root.start(), root.end(), null);
        }
        Node<C, T> node = castNode(resolved);
        return node::evaluate;
    }

    public <T> Function<C, T> compile(String source, Class<T> expectedType) {
        return compile(source, TypeToken.of(expectedType));
    }

    private <T> OperandDefinition<C> literalDefinition(TypeLiteral<T> literal) {
        TypeToken<T> type = Objects.requireNonNull(literal.type(), "literal.type");
        return new OperandDefinition<>(
                Objects.requireNonNull(literal.pattern(), "literal.pattern"),
                type.toString(), (source, start, end) -> new Node.Literal<>(type, start, end, literal.parse(source.substring(start, end))));
    }

    private OperandDefinition<C> runtimeDefinition(RuntimeValue<C> value) {
        return new OperandDefinition<>(
                Objects.requireNonNull(value.pattern(), "runtimeValue.pattern"),
                "runtime value", (source, start, end) -> boundRuntime(
                        Objects.requireNonNull(value.bind(source.substring(start, end)), "runtimeValue.bound"), start, end));
    }

    private <T> Node<C, T> boundRuntime(RuntimeValue.Bound<C, T> value, int start, int end) {
        return new Node.Runtime<>(value.type(), start, end, value::compute);
    }

    private static <A, B, R> OperatorDefinition<A, B, R> operatorDefinition(Operation<A, B, R> operation) {
        return new OperatorDefinition<>(
                Objects.requireNonNull(operation.pattern(), "operation.pattern"),
                Objects.requireNonNull(operation.aType(), "operation.aType"),
                Objects.requireNonNull(operation.bType(), "operation.bType"),
                Objects.requireNonNull(operation.type(), "operation.type"),
                operation.precedence(), operation);
    }

    private static boolean moreSpecific(OperatorDefinition<?, ?, ?> first, OperatorDefinition<?, ?, ?> second) {
        return second.aType().isAssignableFrom(first.aType()) && second.bType().isAssignableFrom(first.bType())
                && !(first.aType().isAssignableFrom(second.aType()) && first.bType().isAssignableFrom(second.bType()));
    }

    private static <A, R> TransformationDefinition<A, R> transformationDefinition(Transformation<A, R> transformation) {
        return new TransformationDefinition<>(
                Objects.requireNonNull(transformation.pattern(), "transformation.pattern"),
                Objects.requireNonNull(transformation.inputType(), "transformation.inputType"),
                Objects.requireNonNull(transformation.type(), "transformation.type"), transformation);
    }

    @SuppressWarnings("unchecked")
    private static <C, T> Node<C, T> castNode(Node<C, ?> node) {
        // Used only after verifying assignability to the expected result or operand type.
        return (Node<C, T>) node;
    }

    /** Returns a specialized copy, or null when this consumer cannot accept the expression. */
    private Node<C, ?> specialize(Node<C, ?> node, TypeToken<?> expected) {
        if (!(node instanceof Node.Vector<?, ?>)) {
            return expected.isAssignableFrom(node.type()) ? node : null;
        }
        // The input node already fixes the context type to C.
        @SuppressWarnings("unchecked")
        Node.Vector<C, ?> vector = (Node.Vector<C, ?>) node;
        TypeToken<?> resolved = vector.type().specializeList(expected);
        if (!expected.isAssignableFrom(resolved)) return null;
        return specializeVector(vector, resolved.listElementType());
    }

    private <T> Node<C, List<T>> specializeVector(Node.Vector<C, ?> vector, TypeToken<T> elementType) {
        List<Node<C, T>> elements = new ArrayList<>();
        for (Node<C, ?> element : vector.elements()) {
            Node<C, ?> resolved = specialize(element, elementType);
            if (resolved == null) return null;
            elements.add(castNode(resolved));
        }
        return new Node.Vector<>(elementType.listType(), vector.start(), vector.end(), elements);
    }

    @FunctionalInterface
    private interface OperandBinder<C> {
        Node<C, ?> bind(String source, int start, int end);
    }

    private record OperandDefinition<C>(Pattern pattern, String description, OperandBinder<C> binder) {}


    private record OperatorDefinition<A, B, R>(
            Pattern pattern, TypeToken<A> aType, TypeToken<B> bType, TypeToken<R> type,
            int precedence, BiFunction<A, B, R> compute) {}

    private record TransformationDefinition<A, R>(
            Pattern pattern, TypeToken<A> inputType, TypeToken<R> type, Function<A, R> compute) {}

    private record Match<D>(int length, List<D> definitions) {}

    private final class Session {

        private final String source;
        private int cursor;

        private Session(String source) {
            this.source = source;
        }

        private Node<C, ?> expression(long minPrecedence) {
            Node<C, ?> left = operand();
            while (true) {
                skipWhitespace();
                if (       cursor == source.length()
                        || source.charAt(cursor) == ')'
                        || source.charAt(cursor) == ']'
                        || source.charAt(cursor) == ','
                ) {
                    return left;
                }
                Match<OperatorDefinition<?, ?, ?>> match = match(operators, OperatorDefinition::pattern);
                if (match == null) {
                    return left;
                }
                int precedence = match.definitions().getFirst().precedence();
                for (OperatorDefinition<?, ?, ?> definition : match.definitions()) {
                    if (definition.precedence() != precedence) {
                        throw error("Operator overloads must agree on precedence", cursor, cursor + match.length(), null);
                    }
                }
                if (precedence < minPrecedence) {
                    return left;
                }
                int operatorStart = cursor;
                cursor += match.length();
                // Widen before adding so Integer.MAX_VALUE precedence remains left-associative.
                Node<C, ?> right = expression((long) precedence + 1);
                left = operation(match, operatorStart, left, right);
            }
        }

        private Node<C, ?> operand() {
            skipWhitespace();
            int start = cursor;
            if (       cursor == source.length()
                    || source.charAt(cursor) == ')'
                    || source.charAt(cursor) == ']'
                    || source.charAt(cursor) == ','
            ) {
                throw error("Expected an operand", cursor, Math.min(cursor + 1, source.length()), null);
            }
            if (source.charAt(cursor) == '[') {
                return vector();
            }
            if (source.charAt(cursor) == '(') {
                cursor++;
                Node<C, ?> nested = expression(Long.MIN_VALUE);
                skipWhitespace();
                if (cursor == source.length() || source.charAt(cursor) != ')') {
                    throw error("Expected ')' to close '(' at " + start,
                            cursor, Math.min(cursor + 1, source.length()), null);
                }
                cursor++;
                return nested.withSpan(start, cursor);
            }
            Match<OperandDefinition<C>> match = match(operands, OperandDefinition::pattern);
            Match<TransformationDefinition<?, ?>> prefix = match(transformations, TransformationDefinition::pattern);
            if (prefix != null) {
                if (match != null && prefix.length() == match.length()) {
                    throw error("Ambiguous operand: a value and a transformation match", start, start + match.length(), null);
                }
                if (match == null || prefix.length() > match.length()) {
                    cursor += prefix.length();
                    return transformation(prefix, start, operand());
                }
            }
            if (match == null) {
                throw error("Unrecognized operand", start, start + 1, null);
            }
            if (match.definitions().size() != 1) {
                throw error("Ambiguous operand: multiple definitions match",
                        start, start + match.length(), null);
            }
            cursor += match.length();
            return bindOperand(match.definitions().getFirst(), start, cursor);
        }

        private Node<C, ?> vector() {
            int start = cursor++;
            skipWhitespace();
            if (cursor < source.length() && source.charAt(cursor) == ']') {
                cursor++;
                return bindVector(TypeToken.commonParent(List.of()), start, cursor, List.of());
            }
            List<Node<C, ?>> elements = new ArrayList<>();
            while (true) {
                elements.add(expression(Long.MIN_VALUE));
                skipWhitespace();
                if (cursor < source.length() && source.charAt(cursor) == ']') {
                    cursor++;
                    return bindVector(
                            TypeToken.commonParent(elements.stream().map(Node::type).toList()),
                            start,
                            cursor,
                            elements
                    );
                }
                if (cursor == source.length() || source.charAt(cursor) != ',') {
                    throw error("Expected ',' or ']' in list opened at " + start, cursor, Math.min(cursor + 1, source.length()), null);
                }
                cursor++;
            }
        }

        private <T> Node<C, List<T>> bindVector(TypeToken<T> elementType, int start, int end,
                List<Node<C, ?>> elements) {
            List<Node<C, T>> typed = elements.stream().map(element -> Compiler.<C, T>castNode(element)).toList();
            return new Node.Vector<>(elementType.listType(), start, end, typed);
        }

        private Node<C, ?> bindOperand(OperandDefinition<C> definition, int start, int end) {
            try {
                return definition.binder().bind(source, start, end);
            } catch (RuntimeException exception) {
                throw error("Failed to parse operand as " + definition.description(), start, end, exception);
            }
        }

        private Node<C, ?> operation(
                Match<OperatorDefinition<?, ?, ?>> match,
                int start,
                Node<C, ?> left,
                Node<C, ?> right
        ) {
            List<OperatorDefinition<?, ?, ?>> compatible = new ArrayList<>();
            int end = start + match.length();
            for (OperatorDefinition<?, ?, ?> definition : match.definitions()) {
                if (specialize(left, definition.aType()) != null && specialize(right, definition.bType()) != null) {
                    compatible.add(definition);
                }
            }
            if (compatible.isEmpty()) {
                throw error("No operator '" + source.substring(start, end) + "' accepts "
                        + left.type() + " and " + right.type(), start, end, null);
            }
            List<OperatorDefinition<?, ?, ?>> best = compatible.stream()
                    .filter(candidate -> compatible.stream().noneMatch(other -> moreSpecific(other, candidate)))
                    .toList();
            if (best.size() != 1) {
                throw error("Ambiguous operator for operand types " + left.type() + " and " + right.type(),
                        start, end, null);
            }
            return bindOperation(best.getFirst(), left, right);
        }

        private <A, B, R> Node<C, R> bindOperation(OperatorDefinition<A, B, R> definition,
                Node<C, ?> left, Node<C, ?> right) {
            Node<C, A> lhs = castNode(specialize(left, definition.aType()));
            Node<C, B> rhs = castNode(specialize(right, definition.bType()));
            return new Node.Operation<>(definition.type(), left.start(), right.end(), lhs, rhs, definition.compute());
        }

        private Node<C, ?> transformation(Match<TransformationDefinition<?, ?>> match, int start, Node<C, ?> subject) {
            List<TransformationDefinition<?, ?>> compatible = match.definitions().stream()
                    .filter(definition -> specialize(subject, definition.inputType()) != null).toList();
            List<TransformationDefinition<?, ?>> best = compatible.stream().filter(candidate -> compatible.stream()
                    .noneMatch(other -> candidate.inputType().isAssignableFrom(other.inputType())
                            && !other.inputType().isAssignableFrom(candidate.inputType()))).toList();
            if (best.isEmpty()) {
                throw error("No transformation accepts " + subject.type(), start, start + match.length(), null);
            }
            if (best.size() != 1) {
                throw error("Ambiguous transformation for " + subject.type(), start, start + match.length(), null);
            }
            return bindTransformation(best.getFirst(), start, subject);
        }

        private <A, R> Node<C, R> bindTransformation(TransformationDefinition<A, R> definition, int start, Node<C, ?> subject) {
            Node<C, A> input = castNode(specialize(subject, definition.inputType()));
            return new Node.Transformation<>(definition.type(), start, subject.end(), input, definition.compute());
        }

        private <D> Match<D> match(List<D> definitions, Function<D, Pattern> pattern) {
            Slice remaining = Slice.of(source, cursor);
            int longest = 0;
            List<D> matches = new ArrayList<>();
            for (D definition : definitions) {
                int length;
                try {
                    length = pattern.apply(definition).match(remaining);
                } catch (RuntimeException exception) {
                    throw error("Pattern matching failed", cursor, cursor, exception);
                }
                if (length > remaining.length()) {
                    throw error("Pattern returned a match longer than the remaining source",
                            cursor, source.length(), null);
                }
                if (length > 0 && length >= longest) {
                    if (length > longest) {
                        matches.clear();
                        longest = length;
                    }
                    matches.add(definition);
                }
            }
            return longest == 0 ? null : new Match<>(longest, matches);
        }

        private void skipWhitespace() {
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
                cursor++;
            }
        }

        private CompilationException error(String message, int start, int end, Throwable cause) {
            return new CompilationException(message, source, start, end, cause);
        }
    }
}
