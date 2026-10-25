package com.kntrel.mc.underilla.config.dsl;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Read-only view of a string range. Index zero is the start of this view; characters outside
 * the range are inaccessible. Indices and lengths count UTF-16 code units, as in {@link String}.
 * Each iterator has an independent cursor and does not change the view.
 */
public interface Slice extends CharSequence, Iterable<Character> {

    static Slice of(String source) {
        Objects.requireNonNull(source, "source");
        return of(source, 0, source.length());
    }

    /** Returns a view from {@code start} through the end of the string. */
    static Slice of(String source, int start) {
        Objects.requireNonNull(source, "source");
        return of(source, start, source.length());
    }

    /** Returns a view of {@code [start, end)}, without copying the string's characters. */
    static Slice of(String source, int start, int end) {
        Objects.requireNonNull(source, "source");
        Objects.checkFromToIndex(start, end, source.length());
        return new StringSlice(source, start, end);
    }

    /** Returns a view of {@code [start, end)}, using indices relative to this slice. */
    @Override
    Slice subSequence(int start, int end);

    default Slice subSlice(int start, int end) {
        return subSequence(start, end);
    }

    /** Returns only the text visible in this slice. */
    @Override
    String toString();

    @Override
    default Iterator<Character> iterator() {
        return new Iterator<>() {
            private int cursor;

            @Override
            public boolean hasNext() {
                return cursor < length();
            }

            @Override
            public Character next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return charAt(cursor++);
            }
        };
    }
}
