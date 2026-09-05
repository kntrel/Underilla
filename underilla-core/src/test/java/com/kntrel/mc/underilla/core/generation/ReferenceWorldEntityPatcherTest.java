package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jkantrell.nbt.tag.CompoundTag;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.reader.EntityView;
import org.junit.jupiter.api.Test;

class ReferenceWorldEntityPatcherTest {

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");

    @Test
    void copiesEntitiesFromTheCorrespondingReferenceChunk() {
        EntityView entity = entity("minecraft:armor_stand", 3955);
        TestChunkGrid reference = chunk(2, -3);
        reference.addEntity(entity);
        ReferenceWorldEntityPatcher patcher = new ReferenceWorldEntityPatcher(
                new TestWorld().addChunk(reference));
        TestChunkGrid target = chunk(2, -3);

        patcher.patch(target);

        assertEquals(1, target.getEntities().size());
        assertEquals("minecraft:armor_stand", target.getEntities().getFirst().tag().getString("id"));
        assertEquals(3955, target.getEntities().getFirst().dataVersion());
    }

    @Test
    void doesNothingWhenTheReferenceChunkIsMissing() {
        TestChunkGrid target = chunk(2, -3);

        new ReferenceWorldEntityPatcher(new TestWorld()).patch(target);

        assertTrue(target.getEntities().isEmpty());
    }

    private static EntityView entity(String id, int dataVersion) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        return new EntityView(tag, dataVersion);
    }

    private static TestChunkGrid chunk(int chunkX, int chunkZ) {
        return new TestChunkGrid(chunkX, chunkZ, 0, 4, AIR, PLAINS);
    }
}
