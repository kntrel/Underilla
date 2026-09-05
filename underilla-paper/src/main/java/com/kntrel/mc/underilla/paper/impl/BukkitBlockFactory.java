package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import org.bukkit.Material;

public final class BukkitBlockFactory implements BlockFactory {

    @Override
    public Block air() { return new BukkitBlock(Material.AIR.createBlockData()); }

    @Override
    public Block create(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null) {
            throw new IllegalArgumentException("Unknown Bukkit material: " + name);
        }
        return new BukkitBlock(material.createBlockData());
    }
}
