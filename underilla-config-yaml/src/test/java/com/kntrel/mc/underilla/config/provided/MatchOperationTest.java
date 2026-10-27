package com.kntrel.mc.underilla.config.provided;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.dsl.Compiler;
import com.kntrel.mc.underilla.config.dsl.Grammar;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;
import com.kntrel.mc.underilla.config.entity.Identifiable;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.Test;

class MatchOperationTest {
    private static final ID STONE = ID.of("minecraft:stone");

    @Test
    void matchesEntireStringsWithTheRegexOnTheRight() {
        var operation = new MatchOperation();
        assertTrue(operation.apply("minecraft:stone", "minecraft:.*"));
        assertFalse(operation.apply("minecraft:stone", "stone"));
        assertFalse(operation.apply("minecraft:stone", "minecraft:dirt"));
        assertTrue(operation.apply("", ".*"));
    }

    @Test
    void matchesIdsIncludingTheirNamespace() {
        var operation = new IdentityMatchOperation();
        assertTrue(operation.apply(STONE, "minecraft:st.*"));
        assertFalse(operation.apply(STONE, "stone"));
        assertFalse(operation.apply(STONE, "other:.*"));
    }

    @Test
    void matchesIdentifiableIds() {
        var operation = new IdentifiableMatchOperation();
        Identifiable candidate = () -> STONE;
        assertTrue(operation.apply(candidate, "minecraft:st.*"));
        assertFalse(operation.apply(candidate, "stone"));
        assertFalse(operation.apply(candidate, "minecraft:dirt"));
    }

    @Test
    void rejectsInvalidRegexes() {
        assertThrows(PatternSyntaxException.class, () -> new MatchOperation().apply("stone", "["));
        assertThrows(PatternSyntaxException.class, () -> new IdentityMatchOperation().apply(STONE, "["));
        assertThrows(PatternSyntaxException.class, () -> new IdentifiableMatchOperation().apply(() -> STONE, "["));
    }

    @Test
    void compilerResolvesOverloadsAndBooleanPrecedence() throws Exception {
        var compiler = Compiler.of(Grammar.with(Identifiable.class).standard()
                .operation(new MatchOperation())
                .operation(new IdentityMatchOperation())
                .operation(new IdentifiableMatchOperation())
                .literal(ID.class, Pattern.regex("@[a-z0-9._-]+:[a-z0-9/._-]+"), text -> ID.of(text.substring(1)))
                .runtime(Identifiable.class, Pattern.keyword("candidate"), context -> context)
                .done());
        Identifiable candidate = () -> STONE;
        assertTrue(compiler.compile("'minecraft:stone' match 'minecraft:.*'", Boolean.class).apply(candidate));
        assertTrue(compiler.compile("@minecraft:stone match 'minecraft:.*'", Boolean.class).apply(candidate));
        assertTrue(compiler.compile("candidate match 'minecraft:.*'", Boolean.class).apply(candidate));
        assertFalse(compiler.compile("candidate match 'minecraft:dirt'", Boolean.class).apply(candidate));
        assertFalse(compiler.compile("candidate match 'minecraft:.*' and false", Boolean.class).apply(candidate));
        assertTrue(compiler.compile("false or candidate match 'minecraft:.*'", Boolean.class).apply(candidate));
        assertThrows(DslCompilationException.class, () -> compiler.compile("candidate match @minecraft:stone", Boolean.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("1 match '.*'", Boolean.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("candidate matcher '.*'", Boolean.class));
    }
}
