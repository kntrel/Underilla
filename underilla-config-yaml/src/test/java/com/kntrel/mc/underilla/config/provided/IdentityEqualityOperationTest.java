package com.kntrel.mc.underilla.config.provided;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;
import com.kntrel.mc.underilla.config.dsl.Compiler;
import com.kntrel.mc.underilla.config.dsl.Grammar;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.entity.Identifiable;
import com.kntrel.mc.underilla.core.api.ID;
import org.junit.jupiter.api.Test;

class IdentityEqualityOperationTest {
    private static final ID STONE = ID.of("minecraft:stone");
    private static final ID DIRT = ID.of("minecraft:dirt");

    @Test
    void comparesIdsAndNegatesInequality() throws Exception {
        Identifiable candidate = () -> STONE;
        var equality = new IdentityEqualityOperation();
        var inequality = new IdentityEqualityOperation(true);
        assertTrue(equality.apply(candidate, ID.of("minecraft:stone")));
        assertFalse(equality.apply(candidate, DIRT));
        assertFalse(inequality.apply(candidate, ID.of("minecraft:stone")));
        assertTrue(inequality.apply(candidate, DIRT));
    }

    @Test
    void compilerPrefersIdentityOverGenericEqualityAndUsesEqualityPrecedence() throws Exception {
        var compiler = compiler(true);
        Identifiable candidate = () -> STONE;
        assertTrue(compiler.compile("candidate = @minecraft:stone", Boolean.class).apply(candidate));
        assertFalse(compiler.compile("candidate = @minecraft:dirt", Boolean.class).apply(candidate));
        assertFalse(compiler.compile("candidate != @minecraft:stone", Boolean.class).apply(candidate));
        assertTrue(compiler.compile("candidate!=@minecraft:dirt", Boolean.class).apply(candidate));
        assertFalse(compiler.compile("candidate = @minecraft:stone and false", Boolean.class).apply(candidate));
        assertTrue(compiler.compile("false or candidate != @minecraft:dirt", Boolean.class).apply(candidate));
        assertTrue(compiler.compile("1 = 1", Boolean.class).apply(candidate));
        assertTrue(compiler.compile("1 != 2", Boolean.class).apply(candidate));
    }

    @Test
    void operationRequiresIdentifiableOnTheLeftAndIdOnTheRight() throws Exception {
        var compiler = compiler(false);
        assertThrows(DslCompilationException.class, () -> compiler.compile("@minecraft:stone = candidate", Boolean.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("candidate != candidate", Boolean.class));
    }

    private static Compiler<Identifiable> compiler(boolean standard) {
        var builder = Grammar.with(Identifiable.class);
        if (standard) builder.standard();
        return Compiler.of(builder
                .operation(new IdentityEqualityOperation())
                .operation(new IdentityEqualityOperation(true))
                .literal(ID.class, Pattern.regex("@[a-z0-9._-]+:[a-z0-9/._-]+"), text -> ID.of(text.substring(1)))
                .runtime(Identifiable.class, Pattern.keyword("candidate"), context -> context)
                .done());
    }
}
