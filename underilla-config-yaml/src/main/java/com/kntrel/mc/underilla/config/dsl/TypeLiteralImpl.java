package com.kntrel.mc.underilla.config.dsl;

import com.kntrel.mc.underilla.config.util.TypeToken;
import java.util.Objects;
import java.util.function.Function;

record TypeLiteralImpl<T>(
        TypeToken<T> type,
        Pattern pattern,
        Function<String, T> decoder
) implements TypeLiteral<T> {

    TypeLiteralImpl {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(decoder, "decoder");
    }

    @Override
    public T parse(String matched) {
        return decoder.apply(Objects.requireNonNull(matched, "matched"));
    }
}
