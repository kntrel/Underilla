package com.kntrel.mc.underilla.paper.listener;

import com.kntrel.mc.underilla.core.api.scope.ChunkSnapshot;
import com.kntrel.mc.underilla.core.api.scope.StructureScope;
import com.kntrel.mc.underilla.core.api.scope.StructureSnapshot;
import com.kntrel.mc.underilla.core.api.scope.WorldSnapshot;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.paper.impl.BukkitIDs;
import com.kntrel.mc.underilla.paper.impl.BukkitWorldInfo;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.AsyncStructureSpawnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StructureEventListener implements Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructureEventListener.class);
    private final Map<String, Integer> structureCount = new ConcurrentHashMap<>();
    private final Function<String, WorldGenerationPlan> plans;
    private final BooleanSupplier debugEnabled;
    private final Function<AsyncStructureSpawnEvent, StructureScope> scopes;

    public StructureEventListener(Function<String, WorldGenerationPlan> plans, BooleanSupplier debugEnabled) {
        this(plans, debugEnabled, StructureEventListener::snapshot);
    }

    StructureEventListener(Function<String, WorldGenerationPlan> plans, BooleanSupplier debugEnabled,
                           Function<AsyncStructureSpawnEvent, StructureScope> scopes) {
        this.plans = Objects.requireNonNull(plans, "plans");
        this.debugEnabled = Objects.requireNonNull(debugEnabled, "debugEnabled");
        this.scopes = Objects.requireNonNull(scopes, "scopes");
    }

    public Map<String, Integer> getStructureCount() { return structureCount; }

    @EventHandler(ignoreCancelled = true)
    public void onStructureSpawn(AsyncStructureSpawnEvent event) {
        WorldGenerationPlan plan = plans.apply(event.getWorld().getName());
        if (plan == null) {
            return;
        }
        StructureScope scope = scopes.apply(event);
        if (!plan.allowsStructure(scope)) {
            event.setCancelled(true);
            return;
        }
        String name = scope.structure().id().toString();
        if (debugEnabled.getAsBoolean()) {
            LOGGER.info("{} spawned at {} {}", name, event.getChunkX(), event.getChunkZ());
        }
        structureCount.merge(name, 1, Integer::sum);
    }

    private static StructureScope snapshot(AsyncStructureSpawnEvent event) {
        var key = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE)
                .getKeyOrThrow(event.getStructure());
        var box = event.getBoundingBox();
        var bounds = new StructureSnapshot.Bounds(box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ());
        // Paper supplies an origin chunk and bounds, but no exact origin or biome.
        // Reading the generating chunk from this asynchronous event is unsafe.
        return StructureScope.of(WorldSnapshot.of(new BukkitWorldInfo(event.getWorld())),
                new ChunkSnapshot(event.getChunkX(), event.getChunkZ()), null,
                new StructureSnapshot(BukkitIDs.from(key), null, bounds));
    }
}
