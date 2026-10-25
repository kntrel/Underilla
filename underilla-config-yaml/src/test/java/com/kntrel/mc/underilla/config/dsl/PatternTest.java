package com.kntrel.mc.underilla.config.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.dsl.Slice;
import org.junit.jupiter.api.Test;

class PatternTest {

    @Test
    void literalMatchesOnlyTheExactPrefixWithoutSkippingWhitespace() {
        Pattern literal = Pattern.literal("==");

        assertEquals(2, literal.match(Slice.of("==value")));
        assertEquals(2, literal.match(Slice.of("prefix==tail", 6)));
        assertEquals(0, literal.match(Slice.of(" ==")));
        assertEquals(0, literal.match(Slice.of("x==")));
        assertEquals(0, literal.match(Slice.of("=")));
        assertEquals(0, literal.match(Slice.of("")));
        assertEquals(0, Pattern.literal("or").match(Slice.of("OR")));
        assertEquals(2, Pattern.literal("or").match(Slice.of("orange")));
    }

    @Test
    void keywordChecksTheTrailingIdentifierBoundary() {
        Pattern keyword = Pattern.keyword("or");

        for (String source : new String[] {"or", "or next", "or(next)", "or=next"}) {
            assertEquals(2, keyword.match(Slice.of(source)), source);
        }
        for (String source : new String[] {"orange", "or1", "or_name", "or$", "or\u00E9",
                "or\uD801\uDC00", "OR", " or", "o"}) {
            assertEquals(0, keyword.match(Slice.of(source)), source);
        }
    }

    @Test
    void patternsCannotReadBeyondSliceBoundaries() {
        assertEquals(2, Pattern.keyword("or").match(Slice.of("orange", 0, 2)));
        assertEquals(0, Pattern.literal("orange").match(Slice.of("orange", 0, 2)));
        assertEquals(0, Pattern.delimited("\"", "\"").match(Slice.of("\"abc\"", 0, 4)));
        assertEquals(0, Pattern.delimited("\"", "\"").match(Slice.of("\"abc\"", 1)));
    }

    @Test
    void delimitedIncludesDelimitersWhitespaceAndEmptyContents() {
        Pattern quoted = Pattern.delimited("\"", "\"");

        String text = "\"minecraft:caves with spaces\"";
        assertEquals(text.length(), quoted.match(Slice.of(text + " in ref.biomes.tags")));
        assertEquals(2, quoted.match(Slice.of("\"\" tail")));
        assertEquals(5, quoted.match(Slice.of("\"a\nb\"")));
        assertEquals(3, quoted.match(Slice.of("\"a\"\"b\"")));
        assertEquals(0, quoted.match(Slice.of("\"unterminated")));
        assertEquals(0, quoted.match(Slice.of(" \"value\"")));
        assertEquals(0, quoted.match(Slice.of("value\"")));
        assertEquals(0, quoted.match(Slice.of("")));
    }

    @Test
    void supportsDifferentAndMultiCharacterDelimitersWithoutNesting() {
        Pattern comment = Pattern.delimited("/*", "*/");

        assertEquals(4, comment.match(Slice.of("/**/")));
        assertEquals(10, comment.match(Slice.of("/*a*b**c*/tail")));
        assertEquals(0, comment.match(Slice.of("/*unterminated*")));
        assertEquals(3, Pattern.delimited("[", "]").match(Slice.of("[a]tail")));
        assertEquals(3, Pattern.delimited("[", "]").match(Slice.of("[[]]")));
        assertEquals(4, Pattern.delimited("aa", "aa").match(Slice.of("aaaa")));
    }

    @Test
    void escapedDelimiterAndEscapedEscapeStayInsideTheMatch() {
        Pattern quoted = Pattern.delimited("\"", "\"", '\\');
        String escapedQuote = "\"a\\\"b\"";
        String escapedEscape = "\"a\\\\\"";
        String both = "\"a\\\\\\\"b\"";

        assertEquals(escapedQuote.length(), quoted.match(Slice.of(escapedQuote + " tail")));
        assertEquals(escapedEscape.length(), quoted.match(Slice.of(escapedEscape + " tail")));
        assertEquals(both.length(), quoted.match(Slice.of(both + " tail")));
        assertEquals(0, quoted.match(Slice.of("\"a\\\"")));
        assertEquals(0, quoted.match(Slice.of("\"a\\")));
        assertEquals(escapedQuote.length(), Pattern.delimited("\"", "\"").match(Slice.of(escapedQuote)));

        String custom = "<a!>b>";
        assertEquals(custom.length(), Pattern.delimited("<", ">", '!').match(Slice.of(custom)));
        String multiple = "/*a\\*/b*/";
        assertEquals(multiple.length(), Pattern.delimited("/*", "*/", '\\').match(Slice.of(multiple)));
    }

    @Test
    void matchesTheDslExampleOneSectionAtATime() {
        String source = "\"minecraft:caves\" in ref.biomes.tags";
        Slice remaining = Slice.of(source);
        int literalLength = Pattern.delimited("\"", "\"", '\\').match(remaining);

        assertEquals("\"minecraft:caves\"", remaining.subSlice(0, literalLength).toString());
        remaining = remaining.subSlice(literalLength + 1, remaining.length());
        int operatorLength = Pattern.keyword("in").match(remaining);
        assertEquals("in", remaining.subSlice(0, operatorLength).toString());
        remaining = remaining.subSlice(operatorLength + 1, remaining.length());
        assertEquals("ref.biomes.tags", remaining.toString());
        assertEquals(remaining.length(), Pattern.literal("ref.biomes.tags").match(remaining));
    }

    @Test
    void patternsCanBeAttachedAsLambdasAndReusedAfterIteration() {
        Pattern digits = src -> {
            int length = 0;
            for (char character : src) {
                if (!Character.isDigit(character)) {
                    break;
                }
                length++;
            }
            return length;
        };
        Slice slice = Slice.of("prefix123tail", 6);
        slice.iterator().next();

        assertEquals(3, digits.match(slice));
        assertEquals(3, digits.match(slice));
        assertEquals(3, Pattern.literal("123").match(slice));
    }

    @Test
    void rejectsNullAndEmptyDefinitionsAndNullSources() {
        assertThrows(NullPointerException.class, () -> Pattern.literal(null));
        assertThrows(IllegalArgumentException.class, () -> Pattern.literal(""));
        assertThrows(NullPointerException.class, () -> Pattern.keyword(null));
        assertThrows(IllegalArgumentException.class, () -> Pattern.keyword(""));
        assertThrows(NullPointerException.class, () -> Pattern.delimited(null, "\""));
        assertThrows(NullPointerException.class, () -> Pattern.delimited("\"", null));
        assertThrows(IllegalArgumentException.class, () -> Pattern.delimited("", "\""));
        assertThrows(IllegalArgumentException.class, () -> Pattern.delimited("\"", ""));
        assertThrows(NullPointerException.class, () -> Pattern.literal("a").match(null));
        assertThrows(NullPointerException.class, () -> Pattern.keyword("a").match(null));
        assertThrows(NullPointerException.class, () -> Pattern.delimited("[", "]").match(null));
    }
}
