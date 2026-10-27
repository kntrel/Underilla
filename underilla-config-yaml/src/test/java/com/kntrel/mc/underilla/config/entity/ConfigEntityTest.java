package com.kntrel.mc.underilla.config.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.property.Properties;
import com.kntrel.mc.underilla.config.property.Property;
import com.kntrel.mc.underilla.config.property.PropertyRepository;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigEntityTest {
    private static final BlockSnapshot BLOCK = new BlockSnapshot(-31, 40, 50, ID.of("minecraft:oak_slab"),
            false, true, false, true, true);

    @Test
    void snapshotFactoriesExposeIndependentAndNestedPropertiesThroughTheRepository() {
        World world = World.of(new WorldSnapshot("generated", ID.of("overworld"), -64, 320, 123));
        Chunk chunk = Chunk.of(ChunkSnapshot.of(-2, 3), world);
        Biome biome = Biome.of(new BiomeSnapshot(ID.of("plains"), -8, 10, 12), Set.of());
        Block block = Block.of(BLOCK, Set.of(), true, Map.of("waterlogged", true));
        Entity entity = Entity.of(new EntitySnapshot(ID.of("item")), Set.of());
        Structure structure = Structure.of(StructureSnapshot.of(ID.of("mineshaft"), -31, 40, 50,
                new StructureSnapshot.Bounds(-32, 35, 48, -16, 55, 64)), Set.of());
        List<Property<?, ?>> definitions = new ArrayList<>();
        for (Class<?> type : List.of(World.class, Chunk.class, Biome.class, Block.class, Entity.class,
                Structure.class, Structure.Origin.class, Structure.Bounds.class)) definitions.addAll(Properties.of(type));
        var properties = PropertyRepository.from(definitions);
        assertEquals(123L, properties.get(World.class, "seed", long.class).orElseThrow().get(world));
        assertEquals(-64, world.minY());
        assertEquals(320, world.maxY());
        assertSame(world, chunk.world());
        assertEquals(-32, chunk.minX());
        assertEquals(-16, chunk.maxX());
        assertEquals(48, chunk.minZ());
        assertEquals(64, chunk.maxZ());
        assertEquals(-8, biome.cellX());
        assertEquals(10, biome.cellY());
        assertEquals(12, biome.cellZ());
        assertEquals(BLOCK.id(), properties.get(Block.class, "id", ID.class).orElseThrow().get(block));
        assertEquals("minecraft", block.namespace());
        assertEquals("oak_slab", block.name());
        assertEquals(1, block.localX());
        assertEquals(2, block.localZ());
        assertTrue(block.waterlogged());
        assertEquals(ID.of("item"), entity.id());
        assertEquals(-31, structure.origin().x());
        assertEquals(16.0, structure.bounds().width());
        assertEquals(20.0, structure.bounds().height());
        assertEquals(16.0, structure.bounds().depth());
        for (Class<?> type : List.of(Biome.class, Block.class, Entity.class, Structure.class)) {
            assertTrue(properties.getAll(type).stream()
                    .noneMatch(property -> Set.of("world", "chunk", "biome").contains(property.name())));
        }
        assertTrue(Properties.of(block.getClass()).stream().noneMatch(property -> property.name().equals("snapshot")));
    }

    @Test
    void factoriesFreezeTagsAndTypedBlockStateWithoutRetainingMutableCollections() {
        ID tag = ID.of("example:selected");
        Set<ID> tags = new HashSet<>(Set.of(tag));
        Map<String, Object> state = new HashMap<>(Map.of("waterlogged", true, "level", 3, "facing", "north"));
        Biome biome = Biome.of(new BiomeSnapshot(ID.of("plains")), tags);
        Block block = Block.of(BLOCK, tags, true, state);
        Entity entity = Entity.of(new EntitySnapshot(ID.of("item")), tags);
        Structure structure = Structure.of(StructureSnapshot.of(ID.of("mineshaft")), tags);
        tags.clear();
        state.clear();
        assertEquals(Set.of(tag), biome.tags());
        assertEquals(Set.of(tag), block.tags());
        assertEquals(Set.of(tag), entity.tags());
        assertEquals(Set.of(tag), structure.tags());
        assertEquals(Map.of("waterlogged", true, "level", 3, "facing", "north"), block.state());
        assertThrows(UnsupportedOperationException.class, () -> block.state().clear());
        assertThrows(UnsupportedOperationException.class, () -> block.tags().clear());
    }

    @Test
    void invalidStateValuesAreRejectedAndUnavailableStructureDetailsRemainNull() {
        assertThrows(IllegalArgumentException.class, () -> Block.of(BLOCK, Set.of(), false, Map.of("level", 1.5)));
        assertThrows(IllegalArgumentException.class, () -> Block.of(BLOCK, Set.of(), false, Map.of("nested", List.of(1))));
        var structure = Structure.of(StructureSnapshot.of(ID.of("mineshaft")), Set.of());
        assertNull(structure.origin());
        assertNull(structure.bounds());
    }
}
