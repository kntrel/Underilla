package com.kntrel.mc.underilla.config.ucel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.dsl.Compiler;
import com.kntrel.mc.underilla.config.dsl.Grammar;
import com.kntrel.mc.underilla.config.dsl.Slice;
import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;
import com.kntrel.mc.underilla.core.api.ID;
import org.junit.jupiter.api.Test;

class IdLiteralTest {
    private final Compiler<Void> compiler = Compiler.of(Grammar.with(Void.class).standard()
            .literal(new IdLiteral()).done());

    @Test
    void decodesQualifiedResourceIds() throws Exception {
        assertEquals(ID.of("minecraft:stone"), compiler.compile("@minecraft:stone", ID.class).apply(null));
        assertEquals(ID.of("my_mod-1.2:path/to.some_value-3"),
                compiler.compile("@my_mod-1.2:path/to.some_value-3", ID.class).apply(null));
    }

    @Test
    void defaultsUnqualifiedIdsToMinecraft() throws Exception {
        for (String value : new String[] {"doors", "fences", "stone", "path/to.some_value-3"}) {
            assertEquals(ID.of("minecraft", value), compiler.compile("@" + value, ID.class).apply(null));
        }
        assertTrue(compiler.compile("@doors = @minecraft:doors", Boolean.class).apply(null));
        assertTrue(compiler.compile("@fences in [@doors, @minecraft:fences]", Boolean.class).apply(null));
    }

    @Test
    void cooperatesWithOperatorsAndCollections() throws Exception {
        assertTrue(compiler.compile("@minecraft:stone=@minecraft:stone", Boolean.class).apply(null));
        assertTrue(compiler.compile("@minecraft:stone in [@minecraft:dirt, @minecraft:stone]", Boolean.class).apply(null));
        assertEquals(16, new IdLiteral().pattern().match(Slice.of("@minecraft:stone and true")));
    }

    @Test
    void rejectsMalformedIds() {
        for (String source : new String[] {"minecraft:stone", "@", "@:stone", "@minecraft:",
                "@Minecraft:stone", "@minecraft:Stone", "@minecraft:stone!", "@minecraft:stone:extra"}) {
            assertThrows(DslCompilationException.class, () -> compiler.compile(source, ID.class), source);
        }
    }
}
