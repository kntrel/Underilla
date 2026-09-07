package com.kntrel.mc.underilla.core.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.api.Entity;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityCleanupPatcherTest {

    @Test
    void removesMatchingEntitiesAndTransformsOnlySurvivors() {
        TestEntity item = new TestEntity("ITEM");
        TestEntity armorStand = new TestEntity("ARMOR_STAND");
        TestChunkGrid chunk = new TestChunkGrid(
                0, 0, 0, 1, TestBlock.air("minecraft:air"), new TestBiome("minecraft:plains"))
                .addLiveEntity(item)
                .addLiveEntity(armorStand);
        List<String> transformed = new ArrayList<>();
        EntityCleanupPatcher patcher = new EntityCleanupPatcher(
                entity -> entity.getType().equals("ITEM"),
                entity -> transformed.add(entity.getType()));

        patcher.patch(chunk);

        assertTrue(item.removed);
        assertFalse(armorStand.removed);
        assertEquals(List.of("ARMOR_STAND"), transformed);
    }

    private static final class TestEntity implements Entity {
        private final String type;
        private boolean removed;

        private TestEntity(String type) { this.type = type; }

        @Override
        public String getType() { return type; }

        @Override
        public void remove() { removed = true; }
    }
}
