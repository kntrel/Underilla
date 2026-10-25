package com.kntrel.mc.underilla.config.dsl;

import java.util.Objects;

/** Implementations used by the Pattern factories. All are immutable and safe to reuse. */
final class Patterns {

    private Patterns() {}

    static Pattern literal(String text) {
        requireText(text, "text");
        return src -> {
            Objects.requireNonNull(src, "src");
            return startsWith(src, 0, text) ? text.length() : 0;
        };
    }

    static Pattern keyword(String text) {
        Pattern literal = literal(text);
        return src -> {
            int length = literal.match(src);
            if (length == 0) {
                return 0;
            }
            return length == src.length()
                    || !Character.isJavaIdentifierPart(Character.codePointAt(src, length))
                    ? length : 0;
        };
    }

    static Pattern regex(String expression) {
        java.util.regex.Pattern compiled = java.util.regex.Pattern.compile(expression);
        return source -> {
            Objects.requireNonNull(source, "src");
            var matcher = compiled.matcher(source);
            return matcher.lookingAt() ? matcher.end() : 0;
        };
    }

    static Pattern delimited(String opening, String closing, Character escape) {
        requireText(opening, "opening");
        requireText(closing, "closing");
        return src -> {
            Objects.requireNonNull(src, "src");
            if (!startsWith(src, 0, opening)) {
                return 0;
            }
            int cursor = opening.length();
            while (cursor < src.length()) {
                if (escape != null && src.charAt(cursor) == escape) {
                    if (cursor + 1 == src.length()) {
                        return 0;
                    }
                    cursor += 2;
                } else if (startsWith(src, cursor, closing)) {
                    return cursor + closing.length();
                } else {
                    cursor++;
                }
            }
            return 0;
        };
    }

    private static boolean startsWith(Slice src, int start, String text) {
        if (text.length() > src.length() - start) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (src.charAt(start + i) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static void requireText(String text, String name) {
        Objects.requireNonNull(text, name);
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
