package com.kntrel.mc.underilla.config.dsl;

/** Recognizes a prefix of a slice, without decoding or evaluating it. */
@FunctionalInterface
public interface Pattern {

    /**
     * Matches at index zero. A positive result is the number of UTF-16 code units consumed,
     * including any delimiters, and must not exceed {@code src.length()}. A result of zero or
     * less means no match. Patterns do not search ahead or implicitly skip whitespace.
     */
    int match(Slice src);

    /** Matches the exact, case-sensitive text, with no boundary requirement. */
    static Pattern literal(String text) {
        return Patterns.literal(text);
    }

    /**
     * Matches the exact, case-sensitive keyword followed by the end of the slice or a character
     * that is not a Java identifier part. No leading boundary is checked: the slice starts at
     * the scanner's cursor. Use {@link #literal(String)} for symbols such as {@code =}.
     */
    static Pattern keyword(String text) {
        return Patterns.keyword(text);
    }

    /**
     * Matches a Java regular expression at the start of the slice. The expression is compiled
     * once; each call uses a separate matcher. Zero-length matches return zero (no match).
     * Invalid expressions fail at construction with {@link java.util.regex.PatternSyntaxException}.
     */
    static Pattern regex(String expression) {
        return Patterns.regex(expression);
    }

    /**
     * Matches an opening delimiter through the first closing delimiter, including both.
     * Delimiters are nonempty, case-sensitive strings. Content may contain whitespace.
     * A backslash escapes the next UTF-16 code unit; nesting is not interpreted. An absent
     * closing delimiter or a dangling escape means no match.
     */
    static Pattern delimited(String opening, String closing) {
        return Patterns.delimited(opening, closing, '\\');
    }

    /**
     * As {@link #delimited(String, String)}, but uses the supplied escape character. It consumes
     * itself and the next UTF-16 code unit before checking for a closing delimiter. Escaped delimiters
     * and escaped escape characters remain inside the match. A dangling escape means no match.
     */
    static Pattern delimited(String opening, String closing, char escape) {
        return Patterns.delimited(opening, closing, escape);
    }
}
