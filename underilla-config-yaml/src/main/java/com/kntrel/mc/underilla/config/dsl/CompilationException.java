package com.kntrel.mc.underilla.config.dsl;

/** A compilation failure with a half-open source span measured in UTF-16 code units. */
public final class CompilationException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String source;
    private final int start;
    private final int end;

    CompilationException(String message, String source, int start, int end, Throwable cause) {
        super(message + " at [" + start + ", " + end + ")", cause);
        this.source = source;
        this.start = start;
        this.end = end;
    }

    public String source() {
        return source;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }
}
