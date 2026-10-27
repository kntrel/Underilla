package com.kntrel.mc.underilla.config.provided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.context.BlockContext;
import com.kntrel.mc.underilla.config.context.DualBlockContext;
import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;
import com.kntrel.mc.underilla.config.dsl.Compiler;
import com.kntrel.mc.underilla.config.dsl.Grammar;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.dsl.RuntimeValue;
import com.kntrel.mc.underilla.config.dsl.Slice;
import com.kntrel.mc.underilla.config.entity.Block;
import com.kntrel.mc.underilla.config.entity.World;
import com.kntrel.mc.underilla.config.property.Properties;
import com.kntrel.mc.underilla.config.property.Property;
import com.kntrel.mc.underilla.config.property.PropertyRepository;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.BlockSnapshot;
import com.kntrel.mc.underilla.core.api.scope.WorldSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ContextRuntimeValueTest {
    private static final PropertyRepository REGISTRY = registry();

    private static PropertyRepository registry() {
        List<Property<?, ?>> properties = new ArrayList<>();
        for (Class<?> type : List.of(DualBlockContext.class, BlockContext.class, Block.class, World.class)) {
            properties.addAll(Properties.of(type));
        }
        return PropertyRepository.from(properties);
    }

    private static <C> Compiler<C> compiler(Class<C> type, PropertyRepository registry) {
        return Compiler.of(Grammar.with(type).standard().runtime(new ContextRuntimeValue<>(type, registry)).done());
    }

    private static BlockContext context(int x, boolean solid) {
        var world = World.of(new WorldSnapshot("test", ID.of("overworld"), -64, 320, 41));
        var block = Block.of(new BlockSnapshot(x, 20, 0, ID.of("stone"), !solid, solid, false, false, true),
                Set.of(), false, Map.of());
        return BlockContext.of(world, null, null, block);
    }

    @Test
    void patternMatchesTheWholeChainAndStopsBeforeAnOperatorOrTrailingWhitespace() throws Exception {
        var value = new ContextRuntimeValue<>(DualBlockContext.class, REGISTRY);
        for (String path : List.of("source", "source.block.x", "source . block . x", "source\n.\tblock . x")) {
            assertEquals(path.length(), value.pattern().match(Slice.of(path)));
            assertEquals(path.length(), value.pattern().match(Slice.of(path + " >= 1")));
            assertEquals(path.length(), value.pattern().match(Slice.of(path + "+1")));
        }
        for (String invalid : List.of("sourceOther", "source_1", "source$", "sourceé", "block", ".source", "")) {
            assertEquals(0, value.pattern().match(Slice.of(invalid)), invalid);
        }
        assertEquals("source.block.missing".length(), value.pattern().match(Slice.of("source.block.missing")));
    }

    @Test
    void bindingFindsTheTerminalTypeWithoutEvaluatingTheContext() throws Exception {
        var value = new ContextRuntimeValue<>(BlockContext.class, REGISTRY);
        assertEquals(TypeToken.of(Block.class), value.bind("block").type());
        assertEquals(TypeToken.of(int.class), value.bind("block.x").type());
        assertEquals(TypeToken.of(long.class), value.bind("world.seed").type());
        var context = context(3, true);
        assertSame(context.block(), value.bind("block").compute(context));
        assertEquals(3, value.bind("block . x").compute(context));
        assertThrows(IllegalArgumentException.class, () -> value.bind("block.missing"));
        assertThrows(IllegalArgumentException.class, () -> value.bind("source.block"));
    }

    @Test
    void compiledGettersUseTheContextSuppliedForEachEvaluation() throws Exception {
        var compiler = compiler(BlockContext.class, REGISTRY);
        var predicate = compiler.compile("block.x+1 >= 4 and block.solid", Boolean.class);
        assertTrue(predicate.apply(context(3, true)));
        assertFalse(predicate.apply(context(2, true)));
        assertFalse(predicate.apply(context(3, false)));
        assertEquals(42L, compiler.compile("world.seed+1", Long.class).apply(context(0, true)));
        assertThrows(DslCompilationException.class, () -> compiler.compile("block.x", String.class));
    }

    @Test
    void chainsCombineWithPrefixAndBinaryOperationsAndWhitespace() throws Exception {
        var compiler = compiler(BlockContext.class, REGISTRY);
        assertTrue(compiler.compile("not block.air and block.x == -1", Boolean.class).apply(context(-1, true)));
        assertEquals(15, compiler.compile("(block.localX)", Integer.class).apply(context(-1, true)));
        assertEquals(20, compiler.compile("block\n . \n y", Integer.class).apply(context(0, true)));
        assertThrows(DslCompilationException.class, () -> compiler.compile("(block).x", Integer.class));
    }

    @Test
    void eachSegmentUsesTheDeclaredTypeReturnedByThePreviousProperty() throws Exception {
        var compiler = compiler(DualBlockContext.class, REGISTRY);
        var expression = compiler.compile("source.block.x < target.block.x and source.world.seed == 41", Boolean.class);
        assertTrue(expression.apply(DualBlockContext.of(context(-1, true), context(1, true))));
        assertFalse(expression.apply(DualBlockContext.of(context(2, true), context(1, true))));
        assertThrows(DslCompilationException.class, () -> compiler.compile("source.world.x", Integer.class));
    }

    @Test
    void gettersRunOnlyAtEvaluationAndInvalidChainsRetainTheirSourceSpan() throws Exception {
        var calls = new AtomicInteger();
        var definitions = new ArrayList<Property<?, ?>>(Properties.of(Block.class));
        definitions.add(Property.from("block", BlockContext.class, Block.class, context -> {
            calls.incrementAndGet();
            return context.block();
        }));
        var compiler = compiler(BlockContext.class, PropertyRepository.from(definitions));
        var expression = compiler.compile("block.x", Integer.class);
        assertEquals(0, calls.get());
        assertEquals(3, expression.apply(context(3, true)));
        assertEquals(1, calls.get());
        var failure = assertThrows(DslCompilationException.class, () -> compiler.compile("block.unknown", Integer.class));
        assertEquals(0, failure.start());
        assertEquals(13, failure.end());
        assertNotNull(failure.getCause());
    }

    @Test
    void rejectsMalformedPathsAndDoesNotInventAliases() throws Exception {
        var compiler = compiler(BlockContext.class, REGISTRY);
        for (String invalid : List.of("source.block.x", "block.x.y", "block.", "block..x", "block.1x", "block. \n")) {
            assertThrows(DslCompilationException.class, () -> compiler.compile(invalid, Integer.class), invalid);
        }
        var value = new ContextRuntimeValue<>(BlockContext.class, REGISTRY);
        assertThrows(IllegalArgumentException.class, () -> value.bind("block.x trailing"));
    }

    @Test
    void compilationAndEvaluationDoNotShareMatchState() throws Exception {
        var compiler = compiler(DualBlockContext.class, REGISTRY);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 40; i++) {
                int x = i;
                tasks.add(executor.submit(() -> compiler.compile(x % 2 == 0 ? "source.block.x" : "target . block . x", Integer.class)
                        .apply(DualBlockContext.of(context(x, true), context(x, false)))));
            }
            for (int i = 0; i < tasks.size(); i++) assertEquals(i, tasks.get(i).get());
        }
    }

    @Test
    void contextDefinitionsUseTheNormalRuntimeValueRegistrationAndRebinding() throws Exception {
        Grammar<BlockContext> grammar = Grammar.with(BlockContext.class).standard()
                .runtime(new ContextRuntimeValue<>(BlockContext.class, REGISTRY)).done();
        var rebound = grammar.withRuntimeValues(List.of(RuntimeValue.of(Integer.class,
                Pattern.literal("number"), (Integer number) -> number)));
        assertEquals(4, Compiler.of(rebound).compile("number+1", Integer.class).apply(3));
        assertThrows(DslCompilationException.class, () -> Compiler.of(rebound).compile("block.x", Integer.class));
        assertThrows(DslCompilationException.class, () -> Compiler.of(grammar.withoutRuntimeValues()).compile("block.x", Integer.class));
        assertThrows(UnsupportedOperationException.class, () -> grammar.runtimeValues().clear());
        assertEquals(1, grammar.runtimeValues().size());
    }
}
