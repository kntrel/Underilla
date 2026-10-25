package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.function.Function;

/** Defines how a literal is recognized and decoded into a value of a declared type. */
public interface TypeLiteral<T> {

    /** Creates a literal definition without invoking its pattern or decoder. */
    static <T> TypeLiteral<T> of(TypeToken<T> type, Pattern pattern, Function<String, T> decoder) {
        return new TypeLiteralImpl<>(type, pattern, decoder);
    }

    /** Convenience overload for types that can be represented by a class. */
    static <T> TypeLiteral<T> of(Class<T> type, Pattern pattern, Function<String, T> decoder) {
        return of(TypeToken.of(type), pattern, decoder);
    }

    TypeToken<T> type();

    Pattern pattern();

    /**
     * Decodes exactly the text selected by the pattern, including any delimiters. The decoder
     * owns delimiter removal, unescaping, and value validation. This method does not match the
     * pattern again; callers should invoke it only after selecting the matched span. Decoder
     * exceptions propagate to the caller.
     */
    T parse(String matched);
}
