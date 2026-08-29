package com.kntrel.mc.underilla.paper.cleaning;

import com.kntrel.mc.underilla.paper.Underilla;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.StringKeys;
import com.kntrel.mc.underilla.paper.selector.Selector;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.LevelReader;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.scheduler.BukkitRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CleanBlocksTask extends FollowableProgressTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(CleanBlocksTask.class);
    private LevelReader levelReader;
    public CleanBlocksTask(int taskID, int tasksCount) {
        super(taskID, tasksCount);
        levelReader = ((CraftWorld) Bukkit.getWorld(selector.getWorldUUID())).getHandle();
    }
    public CleanBlocksTask(int taskID, int tasksCount, Selector selector) {
        super(taskID, tasksCount, selector);
        levelReader = ((CraftWorld) Bukkit.getWorld(selector.getWorldUUID())).getHandle();
    }

    public void run() {
        final long startTime = System.currentTimeMillis();
        final Map<Material, Map<Material, Long>> replacedBlock = new EnumMap<>(Material.class);
        final Map<Material, Long> finalBlock = new EnumMap<>(Material.class);
        new BukkitRunnable() {
            private long processedBlocks = 0;
            @Override
            public void run() {
                long execTime = System.currentTimeMillis();

                while (execTime + 45 > System.currentTimeMillis() && selector.hasNextBlock() && !stop) {
                    Block currentBlock = selector.nextBlock();
                    Material startMaterial = currentBlock.getType();

                    CleanBlocks.cleanBlock(currentBlock, levelReader);

                    // Keep track of removed and final blocks
                    Material finalMaterial = currentBlock.getType();
                    if (startMaterial != finalMaterial) {
                        // add 1 finalMaterial to replacedBlock
                        if (!replacedBlock.containsKey(finalMaterial)) {
                            replacedBlock.put(finalMaterial, new EnumMap<>(Material.class));
                        }
                        replacedBlock.get(finalMaterial).put(startMaterial,
                                replacedBlock.get(finalMaterial).getOrDefault(startMaterial, 0L) + 1L);
                        processedBlocks++;
                    }
                    finalBlock.put(finalMaterial, finalBlock.getOrDefault(finalMaterial, 0L) + 1L);
                }

                if (selector == null || selector.progress() >= 1 || stop) {
                    printProgress(processedBlocks, startTime);

                    LOGGER.info("Cleaning blocks task {} finished in {}", taskID,
                            Duration.ofMillis(System.currentTimeMillis() - startTime));
                    LOGGER.info("Replaced blocks: {}", replacedBlock);
                    LOGGER.info("Final blocks: {}", finalBlock);
                    cancel();
                    Underilla.getInstance().validateTask(StringKeys.STEP_CLEANING_BLOCKS);
                    return;
                } else {
                    printProgressIfNeeded(processedBlocks, startTime);
                }

            }
        }.runTaskTimer(Underilla.getInstance(), 0, 1);
    }
}
