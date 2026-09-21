package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ID;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;

public final class BukkitBlockFactory implements BlockFactory {

    @Override
    public Block air() { return create(ID.of("air")); }

    @Override
    public Block create(ID id) {
        BlockType type = Registry.BLOCK.get(BukkitIDs.toKey(id));
        if (type == null) {
            throw new IllegalArgumentException("Unknown Bukkit block: " + id);
        }
        return new BukkitBlock(type.createBlockData());
    }
}
