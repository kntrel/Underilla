package com.kntrel.mc.underilla.config.dsl.exception;

import java.util.List;
import java.util.OptionalInt;

/** Unexpected input or EOF, including missing operands and delimiters. */
public final class UnexpectedTokenException extends DslCompilationException {
    private static final long serialVersionUID = 1L;
    private final List<String> expectedTokens;
    private final Integer openingCursor;

    public UnexpectedTokenException(String expression, int cursor, int end, List<String> expectedTokens, Integer openingCursor) {
        super("Unexpected input; expected " + String.join(" or ", expectedTokens), expression, cursor, end, null);
        this.expectedTokens = List.copyOf(expectedTokens);
        this.openingCursor = openingCursor;
        if (openingCursor != null && (openingCursor < 0 || openingCursor >= cursor)) {
            throw new IllegalArgumentException("Invalid opening delimiter cursor");
        }
    }

    /** Empty at EOF; otherwise the offending source span. */
    public String actualToken() { return expression().substring(cursor(), end()); }
    public boolean endOfInput() { return cursor() == expression().length(); }
    public List<String> expectedTokens() { return expectedTokens; }
    public OptionalInt openingCursor() { return openingCursor == null ? OptionalInt.empty() : OptionalInt.of(openingCursor); }
}
