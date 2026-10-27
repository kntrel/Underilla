package com.kntrel.mc.underilla.config.ucel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;
import com.kntrel.mc.underilla.config.dsl.Compiler;
import com.kntrel.mc.underilla.config.dsl.Grammar;
import com.kntrel.mc.underilla.config.dsl.Pattern;
import com.kntrel.mc.underilla.config.dsl.Slice;
import com.kntrel.mc.underilla.config.entity.Taggable;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaggedOperationTest {
    private static final ID CAVES = ID.of("minecraft:caves");
    private static final ID COLD = ID.of("example:cold");
    private static final ID WARM = ID.of("example:warm");
    private static final Taggable CANDIDATE = () -> Set.of(CAVES, COLD);

    @Test
    void singleTagChecksMembership() throws Exception {
        var operation = new TaggedOperation();
        assertTrue(operation.apply(CANDIDATE, CAVES));
        assertFalse(operation.apply(CANDIDATE, WARM));
        assertFalse(operation.apply(() -> Set.of(), CAVES));
    }

    @Test
    void anyTagChecksOverlapAndAnEmptySelectionMatchesNothing() throws Exception {
        var operation = new TaggedAnyOperation();
        assertTrue(operation.apply(CANDIDATE, List.of(COLD, WARM)));
        assertFalse(operation.apply(CANDIDATE, List.of(WARM)));
        assertFalse(operation.apply(CANDIDATE, List.of()));
        assertFalse(operation.apply(() -> Set.of(), List.of(COLD)));
    }

    @Test
    void allTagsRequiresTheRhsToBeASubsetAndAnEmptySelectionMatches() throws Exception {
        var operation = new TaggedAllOperation();
        assertTrue(operation.apply(CANDIDATE, List.of(COLD)));
        assertTrue(operation.apply(CANDIDATE, Set.of(COLD, CAVES)));
        assertTrue(operation.apply(CANDIDATE, List.of(COLD, COLD)));
        assertFalse(operation.apply(CANDIDATE, List.of(COLD, CAVES, WARM)));
        assertTrue(operation.apply(CANDIDATE, List.of()));
        assertTrue(operation.apply(() -> Set.of(), List.of()));
    }

    @Test
    void patternsRespectKeywordBoundariesAndPermitWhitespaceBetweenWords() throws Exception {
        var single = new TaggedOperation().pattern();
        var any = new TaggedAnyOperation().pattern();
        var all = new TaggedAllOperation().pattern();
        assertEquals(6, single.match(Slice.of("tagged @minecraft:caves")));
        assertEquals(6, all.match(Slice.of("tagged selection")));
        for (String token : List.of("tagged any", "tagged\tany", "tagged \n any")) {
            assertEquals(token.length(), any.match(Slice.of(token + " selection")));
        }
        for (String token : List.of("tagged all", "tagged\tall", "tagged \n all")) {
            assertEquals(token.length(), all.match(Slice.of(token + " selection")));
        }
        assertEquals(0, single.match(Slice.of("taggedness")));
        assertEquals(0, any.match(Slice.of("taggedanything")));
        assertEquals(0, any.match(Slice.of("tagged anyOther")));
        assertEquals(0, any.match(Slice.of("tagged any$")));
        assertEquals(6, all.match(Slice.of("tagged allOther")));
        assertEquals(0, all.match(Slice.of("tagged_all")));
    }

    @Test
    void compilerSelectsSingleIdAndCollectionOverloadsAndHonorsBooleanPrecedence() throws Exception {
        var compiler = compiler();
        var context = new Context(CANDIDATE, List.of(CAVES, COLD));
        assertTrue(compiler.compile("candidate tagged @minecraft:caves", Boolean.class).apply(context));
        assertFalse(compiler.compile("candidate tagged @example:warm", Boolean.class).apply(context));
        assertTrue(compiler.compile("candidate tagged selection", Boolean.class).apply(context));
        assertTrue(compiler.compile("candidate tagged all selection", Boolean.class).apply(context));
        var mixed = new Context(CANDIDATE, List.of(COLD, WARM));
        assertTrue(compiler.compile("candidate tagged any selection", Boolean.class).apply(mixed));
        assertFalse(compiler.compile("candidate tagged all selection", Boolean.class).apply(mixed));
        assertFalse(compiler.compile("candidate tagged selection", Boolean.class).apply(mixed));
        assertTrue(compiler.compile("candidate tagged @minecraft:caves or false and false", Boolean.class).apply(context));
        assertFalse(compiler.compile("candidate tagged @minecraft:caves and false", Boolean.class).apply(context));
        assertTrue(compiler.compile("candidate tagged\nany selection = true", Boolean.class).apply(mixed));
    }

    @Test
    void compilerAcceptsTagListsForAnyAllAndDefaultOverloads() throws Exception {
        var compiler = compiler();
        var context = new Context(CANDIDATE, List.of());
        assertTrue(compiler.compile("candidate tagged any [@minecraft:caves, @example:warm]",
                Boolean.class).apply(context));
        assertFalse(compiler.compile("candidate tagged all [@minecraft:caves, @example:warm]",
                Boolean.class).apply(context));
        assertTrue(compiler.compile("candidate tagged [@minecraft:caves, @example:cold]",
                Boolean.class).apply(context));
        assertFalse(compiler.compile("candidate tagged any []", Boolean.class).apply(context));
        assertTrue(compiler.compile("candidate tagged all []", Boolean.class).apply(context));
        assertTrue(compiler.compile("candidate tagged []", Boolean.class).apply(context));
        assertThrows(DslCompilationException.class, () -> compiler.compile(
                "candidate tagged any [@minecraft:caves, 'cold']", Boolean.class));
    }

    @Test
    void compilerRejectsWrongOperandAndCollectionElementTypes() throws Exception {
        var compiler = compiler();
        assertThrows(DslCompilationException.class, () -> compiler.compile("text tagged selection", Boolean.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("candidate tagged words", Boolean.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("candidate tagged any words", Boolean.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("candidate tagged all @minecraft:caves", Boolean.class));
        assertThrows(DslCompilationException.class, () -> compiler.compile("candidate tagged any @minecraft:caves", Boolean.class));
    }

    private static Compiler<Context> compiler() {
        return Compiler.of(Grammar.with(Context.class).standard()
                .operation(new TaggedOperation())
                .operation(new TaggedAnyOperation())
                .operation(new TaggedAllOperation())
                .literal(new IdLiteral())
                .runtime(Taggable.class, Pattern.keyword("candidate"), Context::candidate)
                .runtime(new TypeToken<List<ID>>() {}, Pattern.keyword("selection"), Context::selection)
                .runtime(new TypeToken<List<String>>() {}, Pattern.keyword("words"), _ -> List.of("caves"))
                .runtime(String.class, Pattern.keyword("text"), _ -> "caves")
                .done());
    }

    private record Context(Taggable candidate, List<ID> selection) {}
}
