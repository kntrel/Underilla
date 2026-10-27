package com.kntrel.mc.underilla.config.dsl.exception;

import com.kntrel.mc.underilla.config.dsl.DefinitionSignature;
import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.List;
import java.util.stream.Collectors;

/** Shared source rendering; cursors remain UTF-16 offsets, tabs use four-column stops. */
final class Diagnostics {
    private Diagnostics() {}

    static String render(String description, String expression, int cursor, int end) {
        int lineStart = cursor;
        while (lineStart > 0 && expression.charAt(lineStart - 1) != '\n' && expression.charAt(lineStart - 1) != '\r') lineStart--;
        int lineEnd = cursor;
        while (lineEnd < expression.length() && expression.charAt(lineEnd) != '\n' && expression.charAt(lineEnd) != '\r') lineEnd++;
        String prefix = expandTabs(expression.substring(lineStart, cursor));
        String line = expandTabs(expression.substring(lineStart, lineEnd));
        int column = prefix.codePointCount(0, prefix.length());
        String highlighted = expandTabs(expression.substring(lineStart, Math.min(end, lineEnd)));
        int width = Math.max(1, highlighted.codePointCount(0, highlighted.length()) - column);
        return description + "\n" + line + "\n" + " ".repeat(column) + "^" + "~".repeat(width - 1);
    }

    private static String expandTabs(String text) {
        StringBuilder result = new StringBuilder();
        int column = 0;
        for (int point : text.codePoints().toArray()) {
            if (point == '\t') {
                int spaces = 4 - column % 4;
                result.append(" ".repeat(spaces));
                column += spaces;
            } else {
                result.appendCodePoint(point);
                column++;
            }
        }
        return result.toString();
    }

    static String token(String expression, int cursor, int end) {
        if (cursor == expression.length()) return "<end of input>";
        return expression.substring(cursor, end).replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    static String types(List<TypeToken<?>> types) {
        return types.stream().map(Object::toString).collect(Collectors.joining(", ", "(", ")"));
    }

    static String signature(DefinitionSignature definition) {
        String kind = definition.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return kind + " " + types(definition.operandTypes()) + " -> "
                + (definition.resultType() == null ? "<resolved during binding>" : definition.resultType());
    }

    static String candidates(List<DefinitionSignature> definitions) {
        return definitions.stream().map(Diagnostics::signature).collect(Collectors.joining("; "));
    }

    static String cause(Throwable cause) {
        return cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }
}
