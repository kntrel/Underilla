package com.kntrel.mc.underilla.config.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.entity.*;
import com.kntrel.mc.underilla.config.property.Properties;
import com.kntrel.mc.underilla.config.property.Property;
import com.kntrel.mc.underilla.config.property.PropertyRepository;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.*;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ContextTest {
    @Test
    void discoverablePropertiesMirrorCoreScopesUsingConfigEntityTypes() {
        Map<Class<?>, Class<?>> types = Map.ofEntries(
                Map.entry(WorldScope.class, WorldContext.class), Map.entry(BiomeScope.class, BiomeContext.class),
                Map.entry(BlockScope.class, BlockContext.class), Map.entry(StructureScope.class, StructureContext.class),
                Map.entry(EntityScope.class, EntityContext.class), Map.entry(DualWorldScope.class, DualWorldContext.class),
                Map.entry(DualBiomeScope.class, DualBiomeContext.class), Map.entry(DualBlockScope.class, DualBlockContext.class),
                Map.entry(WorldSnapshot.class, World.class), Map.entry(ChunkSnapshot.class, Chunk.class),
                Map.entry(BiomeSnapshot.class, Biome.class), Map.entry(BlockSnapshot.class, Block.class),
                Map.entry(StructureSnapshot.class, Structure.class), Map.entry(EntitySnapshot.class, Entity.class));
        for (Class<?> core : List.of(WorldScope.class, BiomeScope.class, BlockScope.class, StructureScope.class,
                EntityScope.class, DualWorldScope.class, DualBiomeScope.class, DualBlockScope.class)) {
            var expected = Arrays.stream(core.getMethods())
                    .filter(method -> !Modifier.isStatic(method.getModifiers()) && !method.isBridge())
                    .collect(Collectors.toMap(method -> method.getName(), method -> types.get(method.getReturnType())));
            var actual = Properties.of(types.get(core)).stream().collect(Collectors.toMap(Property::name, Property::valueType));
            assertEquals(expected, actual, core.getSimpleName());
            assertEquals(Arrays.stream(core.getInterfaces()).map(types::get).collect(Collectors.toSet()),
                    Set.of(types.get(core).getInterfaces()));
        }
    }

    @Test
    void repositoryTraversesIndependentSourceAndTargetContextsWithoutLosingEntityData() {
        World sourceWorld = World.of(new WorldSnapshot("source", ID.of("overworld"), -64, 320, 10));
        World targetWorld = World.of(new WorldSnapshot("target", ID.of("the_nether"), 0, 256, 20));
        Chunk sourceChunk = Chunk.of(ChunkSnapshot.of(-2, 3), sourceWorld);
        Chunk targetChunk = Chunk.of(ChunkSnapshot.of(-2, 3), targetWorld);
        Biome biome = Biome.of(new BiomeSnapshot(ID.of("plains"), -8, 10, 12), Set.of(ID.of("example:dry")));
        Block block = Block.of(new BlockSnapshot(-31, 40, 50, ID.of("oak_slab"), false, true, false, true, true),
                Set.of(ID.of("minecraft:slabs")), true, Map.of("waterlogged", true));
        BlockContext source = BlockContext.of(sourceWorld, sourceChunk, biome, block);
        BlockContext target = BlockContext.of(targetWorld, targetChunk, null, null);
        DualBlockContext dual = DualBlockContext.of(source, target);
        List<Property<?, ?>> definitions = new ArrayList<>();
        for (Class<?> type : List.of(BlockContext.class, DualBlockContext.class, World.class, Block.class)) {
            definitions.addAll(Properties.of(type));
        }
        var properties = PropertyRepository.from(definitions);
        BlockContext selected = properties.get(DualBlockContext.class, "source", BlockContext.class).orElseThrow().get(dual);
        assertSame(source, selected);
        assertSame(block, properties.get(BlockContext.class, "block", Block.class).orElseThrow().get(selected));
        assertTrue(block.tags().contains(ID.of("minecraft:slabs")));
        assertEquals(true, block.state().get("waterlogged"));
        assertEquals(10, dual.source().world().seed());
        assertEquals(20, dual.target().world().seed());
        assertSame(sourceWorld, source.chunk().world());
        assertSame(targetWorld, target.chunk().world());
        assertNull(dual.target().biome());
        assertNull(dual.target().block());
        assertEquals(Set.of("world", "chunk", "biome", "block"), Properties.of(source.getClass()).stream()
                .map(Property::name).collect(Collectors.toSet()));
    }

    @Test
    void specializedContextsKeepTheirCoreHierarchyAndUnavailableValues() {
        Structure structure = Structure.of(StructureSnapshot.of(ID.of("mineshaft")), Set.of());
        StructureContext structureContext = StructureContext.of(null, null, null, structure);
        Entity entity = Entity.of(new EntitySnapshot(ID.of("item")), Set.of());
        EntityContext entityContext = EntityContext.of(null, null, entity);
        BiomeContext biome = BiomeContext.of(null, null, null);
        WorldContext world = WorldContext.of(null, null);
        assertSame(structure, structureContext.structure());
        assertNull(structureContext.biome());
        assertSame(entity, entityContext.entity());
        assertSame(world, DualWorldContext.of(world, entityContext).source());
        assertSame(structureContext, DualBiomeContext.of(biome, structureContext).target());
        assertEquals(Set.of("world", "chunk", "entity"), Properties.of(entityContext.getClass()).stream()
                .map(Property::name).collect(Collectors.toSet()));
    }
}
