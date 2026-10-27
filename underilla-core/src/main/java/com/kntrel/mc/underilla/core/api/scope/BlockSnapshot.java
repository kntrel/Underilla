package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ID;

/** Read-only properties and global coordinates of one block at the time it is evaluated. */
public record BlockSnapshot(
        int x,
        int y,
        int z,
        ID id,
        boolean isAir,
        boolean isSolid,
        boolean isLiquid,
        boolean isWaterloggable,
        boolean isLegal
) {

    /** Captures a block's current values, or null when the block is unavailable. */
    public static BlockSnapshot of(Block block) {
        return block == null ? null : new BlockSnapshot(block.x(), block.y(), block.z(), block.id(),
                block.isAir(), block.isSolid(), block.isLiquid(), block.isWaterloggable(), block.isLegal());
    }
}
