package com.kntrel.mc.underilla.config.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TypeLiteralTest {

    @Test
    void classFactoryDecodesOnlyTheSelectedSpan() {
        TypeLiteral<Integer> literal = TypeLiteral.of(Integer.class, Pattern.literal("42"), Integer::valueOf);
        Slice source = Slice.of("42 in ref.values");
        int length = literal.pattern().match(source);

        assertEquals(TypeToken.of(Integer.class), literal.type());
        assertEquals(2, length);
        assertEquals(42, literal.parse(source.subSlice(0, length).toString()));
    }

    @Test
    void typeTokenFactoryRetainsParameterizedMetadata() {
        TypeToken<Set<String>> type = new TypeToken<Set<String>>() {};
        Pattern pattern = Pattern.literal("{caves}");
        TypeLiteral<Set<String>> literal = TypeLiteral.of(type, pattern,
                matched -> Set.of(matched.substring(1, matched.length() - 1)));

        assertSame(type, literal.type());
        assertSame(pattern, literal.pattern());
        assertEquals(Set.of("caves"), literal.parse("{caves}"));
    }

    @Test
    void decoderReceivesDelimitersAndOwnsTheirRemoval() {
        TypeLiteral<String> literal = TypeLiteral.of(String.class, Pattern.delimited("\"", "\""),
                matched -> matched.substring(1, matched.length() - 1));
        Slice source = Slice.of("\"minecraft:caves with spaces\" in ref.biomes.tags");
        int length = literal.pattern().match(source);

        assertEquals("minecraft:caves with spaces", literal.parse(source.subSlice(0, length).toString()));
    }

    @Test
    void constructionAndMatchingDoNotParseAndDecodingDoesNotRematch() {
        AtomicInteger matches = new AtomicInteger();
        AtomicInteger decodes = new AtomicInteger();
        Pattern pattern = src -> {
            matches.incrementAndGet();
            return 4;
        };
        TypeLiteral<String> literal = TypeLiteral.of(String.class, pattern, matched -> {
            decodes.incrementAndGet();
            return matched;
        });

        assertEquals(0, matches.get());
        assertEquals(0, decodes.get());
        assertEquals(4, literal.pattern().match(Slice.of("text")));
        assertEquals(0, decodes.get());
        assertEquals("text", literal.parse("text"));
        assertEquals(1, matches.get());
        assertEquals(1, decodes.get());
    }

    @Test
    void preservesDecoderFailures() {
        IllegalArgumentException failure = new IllegalArgumentException("invalid literal");
        TypeLiteral<Integer> literal = TypeLiteral.of(Integer.class, Pattern.literal("bad"), matched -> {
            throw failure;
        });

        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> literal.parse("bad")));
    }

    @Test
    void rejectsNullDefinitionsAndNullMatchedText() {
        Pattern pattern = Pattern.literal("text");
        Function<String, String> decoder = Function.identity();

        assertThrows(NullPointerException.class, () -> TypeLiteral.of((Class<String>) null, pattern, decoder));
        assertThrows(NullPointerException.class, () -> TypeLiteral.of((TypeToken<String>) null, pattern, decoder));
        assertThrows(NullPointerException.class, () -> TypeLiteral.of(String.class, null, decoder));
        assertThrows(NullPointerException.class, () -> TypeLiteral.of(String.class, pattern, null));
        assertThrows(NullPointerException.class, () -> TypeLiteral.of(String.class, pattern, decoder).parse(null));
    }
}
