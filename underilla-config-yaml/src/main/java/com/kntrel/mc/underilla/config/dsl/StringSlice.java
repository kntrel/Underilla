package com.kntrel.mc.underilla.config.dsl;

import java.util.Objects;

/** Immutable string-backed view. The backing string and absolute indices are not exposed. */
final class StringSlice implements Slice {

    private final String source;
    private final int start;
    private final int end;

    StringSlice(String source, int start, int end) {
        this.source = source;
        this.start = start;
        this.end = end;
    }

    @Override
    public int length() {
        return end - start;
    }

    @Override
    public char charAt(int index) {
        Objects.checkIndex(index, length());
        return source.charAt(start + index);
    }

    @Override
    public Slice subSequence(int start, int end) {
        Objects.checkFromToIndex(start, end, length());
        return new StringSlice(source, this.start + start, this.start + end);
    }

    @Override
    public String toString() {
        return source.substring(start, end);
    }
}
