package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.IntPredicate;

/** Shared, immutable definitions installed by Grammar.Builder.standard(). */
final class GrammarStd {

    private static final List<Class<? extends Number>> NUMERIC_TYPES = List.of(
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class
    );
    private static final String FLOATING_LITERAL = "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?";

    static final List<TypeLiteral<?>> LITERALS = List.of(
            TypeLiteral.of(Byte.class, Pattern.regex("[+-]?[0-9]+[bB]"), text -> Byte.valueOf(text.substring(0, text.length() - 1))),
            TypeLiteral.of(Short.class, Pattern.regex("[+-]?[0-9]+[sS]"), text -> Short.valueOf(text.substring(0, text.length() - 1))),
            TypeLiteral.of(Integer.class, Pattern.regex("[+-]?[0-9]+"), Integer::valueOf),
            TypeLiteral.of(Long.class, Pattern.regex("[+-]?[0-9]+[lL]"), text -> Long.valueOf(text.substring(0, text.length() - 1))),
            TypeLiteral.of(Float.class, Pattern.regex(FLOATING_LITERAL + "[fF]"), Float::valueOf),
            TypeLiteral.of(Double.class, Pattern.regex("[+-]?(?:(?:[0-9]+\\.[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|[0-9]+[eE][+-]?[0-9]+)"), Double::valueOf),
            TypeLiteral.of(Double.class, Pattern.regex(FLOATING_LITERAL + "[dD]"), Double::valueOf),
            TypeLiteral.of(Boolean.class, Pattern.keyword("true"), _ -> true),
            TypeLiteral.of(Boolean.class, Pattern.keyword("false"), _ -> false),
            TypeLiteral.of(String.class, Pattern.delimited("\"", "\""), GrammarStd::string),
            TypeLiteral.of(String.class, Pattern.delimited("'", "'"), GrammarStd::string));

    static final List<Operation<?, ?, ?>> OPERATIONS = operations();
    static final List<Transformation<?, ?>> TRANSFORMATIONS = List.of(
            Transformation.of(Boolean.class, Boolean.class, Pattern.keyword("not"), value -> !value),
            Transformation.of(Boolean.class, Boolean.class, Pattern.literal("!"), value -> !value));

    private GrammarStd() {}

    private static List<Operation<?, ?, ?>> operations() {
        List<Operation<?, ?, ?>> result = new ArrayList<>();
        for (String token : List.of("+", "-", "*", "/", "%")) {
            int precedence = token.equals("+") || token.equals("-") ? 50 : 60;
            Pattern pattern = Pattern.literal(token);
            for (Class<? extends Number> a : NUMERIC_TYPES) {
                for (Class<? extends Number> b : NUMERIC_TYPES) {
                    if (a == Double.class || b == Double.class) {
                        result.add(Operation.of(a, b, Double.class, pattern, precedence, arithmetic(Double.class, token)));
                    } else if (a == Float.class || b == Float.class) {
                        result.add(Operation.of(a, b, Float.class, pattern, precedence, arithmetic(Float.class, token)));
                    } else if (a == Long.class || b == Long.class) {
                        result.add(Operation.of(a, b, Long.class, pattern, precedence, arithmetic(Long.class, token)));
                    } else {
                        result.add(Operation.of(a, b, Integer.class, pattern, precedence, arithmetic(Integer.class, token)));
                    }
                }
            }
            // A statically broader Number input has a deterministic Double result type.
            result.add(Operation.of(Number.class, Number.class, Double.class, pattern, precedence, arithmetic(Double.class, token)));
        }
        result.add(Operation.of(String.class, String.class, String.class, Pattern.literal("+"), 50, String::concat));
        for (String token : List.of("<", "<=", ">", ">=")) {
            result.add(Operation.of(Number.class, Number.class, Boolean.class, Pattern.literal(token), 40,
                    numericRelation(token)));
            IntPredicate comparison = relation(token);
            result.add(Operation.of(String.class, String.class, Boolean.class, Pattern.literal(token), 40,
                    (a, b) -> comparison.test(a.compareTo(b))));
        }
        for (String token : List.of("=", "==", "!=")) {
            BiFunction<Object, Object, Boolean> equality = token.equals("!=")
                    ? (a, b) -> !Objects.equals(a, b) : Objects::equals;
            result.add(Operation.of(Object.class, Object.class, Boolean.class, Pattern.literal(token), 30,
                    equality));
            result.add(Operation.of(Number.class, Number.class, Boolean.class, Pattern.literal(token), 30,
                    numericRelation(token)));
        }
        for (String token : List.of("and", "&&")) {
            result.add(Operation.of(Boolean.class, Boolean.class, Boolean.class, operatorPattern(token), 20, (a, b) -> a && b));
        }
        for (String token : List.of("or", "||")) {
            result.add(Operation.of(Boolean.class, Boolean.class, Boolean.class, operatorPattern(token), 10, (a, b) -> a || b));
        }
        result.add(Operation.of(TypeToken.of(Object.class), new TypeToken<Iterable<?>>() {},
                TypeToken.of(Boolean.class), Pattern.keyword("in"), 40, (value, items) -> {
                    for (Object item : items) {
                        if (Objects.equals(value, item)) { return true; }
                    }
                    return false;
                }));
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static <A extends Number, B extends Number, R extends Number> BiFunction<A, B, R> arithmetic(Class<R> resultType, String token) {
        if (resultType == Integer.class) {
            BiFunction<A, B, Integer> compute = switch (token) {
                case "+" -> (a, b) -> a.intValue() + b.intValue();
                case "-" -> (a, b) -> a.intValue() - b.intValue();
                case "*" -> (a, b) -> a.intValue() * b.intValue();
                case "/" -> (a, b) -> a.intValue() / b.intValue();
                case "%" -> (a, b) -> a.intValue() % b.intValue();
                default -> throw new AssertionError(token);
            };
            // Class equality guarantees the callback's result is R; the cast happens only at registration.
            return (BiFunction<A, B, R>) compute;
        } else if (resultType == Long.class) {
            BiFunction<A, B, Long> compute = switch (token) {
                case "+" -> (a, b) -> a.longValue() + b.longValue();
                case "-" -> (a, b) -> a.longValue() - b.longValue();
                case "*" -> (a, b) -> a.longValue() * b.longValue();
                case "/" -> (a, b) -> a.longValue() / b.longValue();
                case "%" -> (a, b) -> a.longValue() % b.longValue();
                default -> throw new AssertionError(token);
            };
            return (BiFunction<A, B, R>) compute;
        } else if (resultType == Float.class) {
            BiFunction<A, B, Float> compute = switch (token) {
                case "+" -> (a, b) -> a.floatValue() + b.floatValue();
                case "-" -> (a, b) -> a.floatValue() - b.floatValue();
                case "*" -> (a, b) -> a.floatValue() * b.floatValue();
                case "/" -> (a, b) -> a.floatValue() / b.floatValue();
                case "%" -> (a, b) -> a.floatValue() % b.floatValue();
                default -> throw new AssertionError(token);
            };
            return (BiFunction<A, B, R>) compute;
        } else if (resultType == Double.class) {
            BiFunction<A, B, Double> compute = switch (token) {
                case "+" -> (a, b) -> a.doubleValue() + b.doubleValue();
                case "-" -> (a, b) -> a.doubleValue() - b.doubleValue();
                case "*" -> (a, b) -> a.doubleValue() * b.doubleValue();
                case "/" -> (a, b) -> a.doubleValue() / b.doubleValue();
                case "%" -> (a, b) -> a.doubleValue() % b.doubleValue();
                default -> throw new AssertionError(token);
            };
            return (BiFunction<A, B, R>) compute;
        } else {
            throw new AssertionError(resultType);
        }
    }

    private static BiFunction<Number, Number, Boolean> numericRelation(String token) {
        IntPredicate relation = relation(token);
        boolean whenNaN = token.equals("!=");
        return (a, b) -> {
            if (floating(a) && Double.isNaN(a.doubleValue()) || floating(b) && Double.isNaN(b.doubleValue())) {
                return whenNaN;
            }
            int comparison;
            if (floating(a) && !Double.isFinite(a.doubleValue()) || floating(b) && !Double.isFinite(b.doubleValue())) {
                comparison = Double.compare(a.doubleValue(), b.doubleValue());
            } else {
                comparison = new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
            }
            return relation.test(comparison);
        };
    }

    private static boolean floating(Number number) {
        return number instanceof Double || number instanceof Float;
    }

    private static IntPredicate relation(String token) {
        return switch (token) {
            case "<" -> comparison -> comparison < 0;
            case "<=" -> comparison -> comparison <= 0;
            case ">" -> comparison -> comparison > 0;
            case ">=" -> comparison -> comparison >= 0;
            case "=", "==" -> comparison -> comparison == 0;
            case "!=" -> comparison -> comparison != 0;
            default -> throw new AssertionError(token);
        };
    }

    private static Pattern operatorPattern(String token) {
        return Character.isLetter(token.charAt(0)) ? Pattern.keyword(token) : Pattern.literal(token);
    }

    private static String string(String source) {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < source.length() - 1; i++) {
            char character = source.charAt(i);
            if (character != '\\') {
                result.append(character);
                continue;
            }
            char escaped = source.charAt(++i);
            switch (escaped) {
                case '\\', '\"', '\'' -> result.append(escaped);
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'u' -> {
                    if (i + 4 >= source.length() - 1) {
                        throw new IllegalArgumentException("Incomplete Unicode escape");
                    }
                    int codeUnit = 0;
                    for (int offset = 1; offset <= 4; offset++) {
                        char digit = source.charAt(i + offset);
                        int value = Character.digit(digit, 16);
                        if (value < 0 || digit > 127) {
                            throw new IllegalArgumentException("Invalid Unicode escape");
                        }
                        codeUnit = (codeUnit << 4) | value;
                    }
                    result.append((char) codeUnit);
                    i += 4;
                }
                default -> throw new IllegalArgumentException("Unknown string escape: " + escaped);
            }
        }
        return result.toString();
    }
}
