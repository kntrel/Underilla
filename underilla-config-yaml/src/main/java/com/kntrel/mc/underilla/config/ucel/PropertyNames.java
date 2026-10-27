package com.kntrel.mc.underilla.config.ucel;

import com.kntrel.mc.underilla.config.dsl.Slice;

/** Standard ASCII property identifiers; punctuation and whitespace terminate a name. */
final class PropertyNames {
    private PropertyNames() {}

    static int length(Slice source, int offset) {
        if (offset >= source.length() || !start(source.charAt(offset))) return 0;
        int end = offset + 1;
        while (end < source.length() && part(source.charAt(end))) end++;
        // A non-ASCII Java identifier continuation must not turn a larger word into a property match.
        if (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) return 0;
        return end - offset;
    }

    private static boolean start(char value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z' || value == '_';
    }

    private static boolean part(char value) { return start(value) || value >= '0' && value <= '9'; }
}
