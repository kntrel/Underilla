package com.kntrel.mc.underilla.config.ucel;

import static org.junit.jupiter.api.Assertions.*;

import com.kntrel.mc.underilla.config.context.*;
import com.kntrel.mc.underilla.config.dsl.Compiler;
import com.kntrel.mc.underilla.config.dsl.exception.DslCompilationException;
import com.kntrel.mc.underilla.config.entity.*;
import com.kntrel.mc.underilla.config.property.Property;
import com.kntrel.mc.underilla.config.property.PropertyRepository;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.config.yaml.YamlConfigLoader;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UCELTest {
    private static BlockContext context(String id, Set<ID> tags) {
        World world = World.of(new WorldSnapshot("test", ID.of("overworld"), -64, 320, 41));
        return BlockContext.of(world, Chunk.of(ChunkSnapshot.of(0, 0), world),
                Biome.of(new BiomeSnapshot(ID.of("plains"), 0, 0, 0), Set.of()),
                Block.of(new BlockSnapshot(0, 20, 0, ID.of(id), false, true, false, false, true),
                        tags, false, Map.of()));
    }

    @Test
    void factoryRegistersStandardAndUcelDefinitions() throws Exception {
        var compiler = UCEL.compilerWith(BlockContext.class);
        var context = context("oak_log", Set.of(ID.of("logs")));
        for (String expression : List.of("block = @minecraft:oak_log", "block != @minecraft:stone",
                "block.id = @minecraft:oak_log", "block tagged @minecraft:logs",
                "block tagged any [@minecraft:logs, @minecraft:leaves]",
                "block tagged all [@minecraft:logs]", "block tagged [@minecraft:logs]",
                "block = @oak_log", "block tagged any [@doors, @fences, @logs]",
                "block match 'minecraft:.*_log'", "block.id match 'minecraft:.*_log'",
                "block.name match 'oak_.*'", "not block.air and block.y + 1 = 21")) {
            assertTrue(compiler.compile(expression, Boolean.class).apply(context), expression);
        }
        assertThrows(DslCompilationException.class, () -> compiler.compile("block.id == @minecraft:oak_log", Boolean.class));
    }

    @Test
    void dualContextAliasesSelectSourceAndKeepTargetIndependent() throws Exception {
        var source = context("sand", Set.of());
        var target = context("stone", Set.of());
        var compiler = UCEL.compilerWith(TypeToken.of(DualBlockContext.class), UCEL.properties());
        var dual = DualBlockContext.of(source, target);
        assertTrue(compiler.compile("block = @minecraft:sand and target.block = @minecraft:stone", Boolean.class).apply(dual));
        assertSame(source.block(), compiler.compile("block", Block.class).apply(dual));
        assertSame(source.biome(), compiler.compile("biome", Biome.class).apply(dual));
        assertSame(source.world(), compiler.compile("world", World.class).apply(dual));
        assertSame(source.chunk(), compiler.compile("chunk", Chunk.class).apply(dual));
        assertSame(source.biome(), UCEL.compilerWith(DualBiomeContext.class)
                .compile("biome", Biome.class).apply(DualBiomeContext.of(source, target)));
        assertSame(source.world(), UCEL.compilerWith(DualWorldContext.class)
                .compile("world", World.class).apply(DualWorldContext.of(source, target)));
    }

    @Test
    void suppliedRepositoriesAndBothContextOverloadsWork() throws Exception {
        var repository = PropertyRepository.from(List.of(Property.from("value", String.class, String.class, value -> value)));
        var byClass = Compiler.of(UCEL.grammarWith(String.class, repository));
        var byToken = UCEL.compilerWith(TypeToken.of(String.class), repository);
        assertTrue(byClass.compile("value match 'oak_.*'", Boolean.class).apply("oak_log"));
        assertTrue(byToken.compile("value = 'oak_log'", Boolean.class).apply("oak_log"));
        assertTrue(UCEL.compilerWith(String.class, repository).compile("value != 'stone'", Boolean.class).apply("oak_log"));
        assertTrue(Compiler.of(UCEL.grammarWith(TypeToken.of(BlockContext.class)))
                .compile("block = @minecraft:stone", Boolean.class).apply(context("stone", Set.of())));
        assertTrue(Compiler.of(UCEL.grammarWith(BlockContext.class))
                .compile("block = @minecraft:stone", Boolean.class).apply(context("stone", Set.of())));
        assertTrue(UCEL.compilerWith(TypeToken.of(BlockContext.class))
                .compile("block = @minecraft:stone", Boolean.class).apply(context("stone", Set.of())));
        assertThrows(DslCompilationException.class, () -> UCEL.compilerWith(String.class)
                .compile("value = 'oak_log'", Boolean.class));
    }

    @Test
    void propertiesIncludeEveryContextEntityAndNestedStructureType() {
        for (Class<?> type : List.of(WorldContext.class, BiomeContext.class, BlockContext.class,
                EntityContext.class, StructureContext.class, DualWorldContext.class,
                DualBiomeContext.class, DualBlockContext.class, World.class, Chunk.class,
                Biome.class, Block.class, Entity.class, Structure.class, Structure.Origin.class, Structure.Bounds.class)) {
            assertFalse(UCEL.properties().getAll(type).isEmpty(), type.getName());
        }
    }

    @Test
    void bundledConfigLoadsAndEvaluatesUsingRealCompilers() {
        var config = new YamlConfigLoader()
                .withCompiler(BlockContext.class, UCEL.compilerWith(BlockContext.class))
                .withCompiler(BiomeContext.class, UCEL.compilerWith(BiomeContext.class))
                .withCompiler(DualBlockContext.class, UCEL.compilerWith(DualBlockContext.class))
                .withCompiler(DualBiomeContext.class, UCEL.compilerWith(DualBiomeContext.class))
                .withCompiler(StructureContext.class, UCEL.compilerWith(StructureContext.class))
                .withCompiler(EntityContext.class, UCEL.compilerWith(EntityContext.class))
                .loadDefaults();
        var passthrough = config.merge().surface().passthroughBlockIf();
        assertTrue(passthrough.test(context("oak_log", Set.of(ID.of("logs")))));
        assertTrue(passthrough.test(context("oak_wood", Set.of())));
        assertFalse(passthrough.test(context("stone", Set.of())));
        assertTrue(config.cleanup().blocks().supportBlocks().getFirst().condition().test(context("sand", Set.of())));
        assertFalse(config.cleanup().blocks().supportBlocks().getFirst().condition().test(context("stone", Set.of())));
        var cave = Biome.of(new BiomeSnapshot(ID.of("lush_caves"), 0, 0, 0), Set.of(ID.of("caves")));
        var biomeContext = BiomeContext.of(null, null, cave);
        assertTrue(config.biomes().target().preserveIf().test(DualBiomeContext.of(biomeContext, biomeContext)));
        assertTrue(config.cleanup().entities().removeIf().test(EntityContext.of(null, null,
                Entity.of(new EntitySnapshot(ID.of("item")), Set.of()))));
    }
}
