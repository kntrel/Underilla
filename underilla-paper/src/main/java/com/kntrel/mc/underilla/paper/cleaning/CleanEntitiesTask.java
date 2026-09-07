package com.kntrel.mc.underilla.paper.cleaning;

import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.paper.Underilla;
import com.kntrel.mc.underilla.paper.impl.BukkitIDs;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.SetEntityTypeKeys;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.StringKeys;
import com.kntrel.mc.underilla.paper.selector.Selector;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CleanEntitiesTask extends FollowableProgressTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(CleanEntitiesTask.class);

    public CleanEntitiesTask(int taskID, int tasksCount) { super(taskID, tasksCount); }
    public CleanEntitiesTask(int taskID, int tasksCount, Selector selector) { super(taskID, tasksCount, selector); }

    public void run() {
        final long startTime = System.currentTimeMillis();
        final Map<ID, Long> removedEntity = new HashMap<>();
        final Map<ID, Long> finalEntity = new HashMap<>();
        new BukkitRunnable() {
            private long processedEntities = 0;
            @Override
            public void run() {
                long execTime = System.currentTimeMillis();
                while (selector != null && execTime + 45 > System.currentTimeMillis() && selector.hasNextBlock() && !stop) {
                    Chunk currentChunk = selector.nextChunk();
                    for (Entity entity : currentChunk.getEntities()) {
                        ID entityType = BukkitIDs.from(Registry.ENTITY_TYPE.getKeyOrThrow(entity.getType()));
                        if (Underilla.getUnderillaConfig().isEntityTypeInSet(
                                SetEntityTypeKeys.CLEAN_ENTITY_TO_REMOVE, entityType)) {
                            entity.remove();
                            removedEntity.put(entityType, removedEntity.getOrDefault(entityType, 0L) + 1);
                        } else {
                            // Final transformation that can be override by other plugins
                            if (Underilla.getInstance().hasEndEntityTransformer()) {
                                Underilla.getInstance().getEndEntityTransformer().accept(entity);
                            }
                            finalEntity.put(entityType, finalEntity.getOrDefault(entityType, 0L) + 1);
                        }
                    }
                }

                if (selector == null || selector.progress() >= 1 || stop) {
                    printProgress(processedEntities, startTime);
                    String finishOrStop = stop ? "stopped" : "finished";
                    LOGGER.info("Cleaning entities task {} {} in {}", taskID, finishOrStop,
                            Duration.ofMillis(System.currentTimeMillis() - startTime));
                    LOGGER.info("Removed entities: {}", removedEntity);
                    LOGGER.info("Final entities: {}", finalEntity);
                    cancel();
                    Underilla.getInstance().validateTask(StringKeys.STEP_CLEANING_ENTITIES);
                    return;
                } else {
                    printProgressIfNeeded(processedEntities, startTime);
                }

            }

        }.runTaskTimer(Underilla.getInstance(), 0, 1);
    }

}
