package com.kntrel.mc.underilla.paper.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.ChunkSnapshot;
import com.kntrel.mc.underilla.core.api.scope.StructureScope;
import com.kntrel.mc.underilla.core.api.scope.StructureSnapshot;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.event.world.AsyncStructureSpawnEvent;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

class StructureEventListenerTest {
    @Test
    void unrelatedWorldIsLeftAloneWithoutCapturingScope() {
        var listener = new StructureEventListener(_ -> null, () -> false,
                _ -> { throw new AssertionError("Unrelated world was sampled"); });
        var event = event("other", 0);
        listener.onStructureSpawn(event);
        assertFalse(event.isCancelled());
        assertTrue(listener.getStructureCount().isEmpty());
    }

    @Test
    void delegatesSelectionToTheWorldsPlanAndCountsOnlyAllowedStructures() {
        var allowed = WorldGenerationPlan.build().done();
        var denied = WorldGenerationPlan.build().structureFilter(_ -> false).done();
        var plans = Map.of("allowed", allowed, "denied", denied);
        var listener = new StructureEventListener(plans::get, () -> false, StructureEventListenerTest::scope);
        var acceptedEvent = event("allowed", 0);
        var rejectedEvent = event("denied", 0);
        listener.onStructureSpawn(acceptedEvent);
        listener.onStructureSpawn(rejectedEvent);
        assertFalse(acceptedEvent.isCancelled());
        assertTrue(rejectedEvent.isCancelled());
        assertEquals(Map.of("minecraft:fortress", 1), listener.getStructureCount());
    }

    @Test
    void leavesFallbackStructuresOutsidePlanCoverageAllowed() {
        var plan = WorldGenerationPlan.build().coverage((x, z) -> x == 0)
                .structures(false).structureFilter(_ -> false).done();
        var listener = new StructureEventListener(_ -> plan, () -> false, StructureEventListenerTest::scope);
        var event = event("world", 1);
        listener.onStructureSpawn(event);
        assertFalse(event.isCancelled());
    }

    private static StructureScope scope(AsyncStructureSpawnEvent event) {
        return StructureScope.of(null, new ChunkSnapshot(event.getChunkX(), event.getChunkZ()), null,
                StructureSnapshot.of(ID.of("minecraft:fortress")));
    }

    private static AsyncStructureSpawnEvent event(String worldName, int chunkX) {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (_, method, _) -> {
                    if (method.getName().equals("getName")) return worldName;
                    throw new AssertionError("Unexpected world access: " + method.getName());
                });
        return new AsyncStructureSpawnEvent(world, null, new BoundingBox(0, 0, 0, 16, 16, 16), chunkX, 0);
    }
}
