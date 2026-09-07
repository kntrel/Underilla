package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

public class BukkitBlock implements Block {
    private static final Set<ID> LIQUID_BLOCKS = Set.of(
            ID.of("water"), ID.of("lava"), ID.of("bubble_column"), ID.of("seagrass"),
            ID.of("tall_seagrass"), ID.of("kelp"), ID.of("kelp_plant"));
    // FIELDS
    private BlockData blockData_;
    private Optional<ID> spawnedType;


    // CONSTRUCTORS
    public BukkitBlock(BlockData blockData) {
        this.blockData_ = blockData;
        this.spawnedType = Optional.empty();
    }


    // GETTERS
    public BlockData getBlockData() { return this.blockData_; }

    public Optional<ID> getSpawnedType() { return this.spawnedType; }
    public void setSpawnedType(String spawnedType) {
        ID id = ID.of(spawnedType);
        if (Registry.ENTITY_TYPE.get(BukkitIDs.toKey(id)) == null) {
            throw new IllegalArgumentException("Unknown Bukkit entity type: " + id);
        }
        this.spawnedType = Optional.of(id);
    }

    // IMPLEMENTATIONS
    @Override
    public boolean isAir() { return this.blockData_.getMaterial().isAir(); }

    @Override
    public boolean isSolid() { return this.blockData_.getMaterial().isSolid(); }

    @Override
    public boolean isLiquid() {
        return LIQUID_BLOCKS.contains(id()) || isWaterLogged();
    }

    @Override
    public boolean isWaterloggable() { return this.blockData_ instanceof Waterlogged; }

    public boolean isWaterLogged() { return this.blockData_ instanceof Waterlogged waterlogged && waterlogged.isWaterlogged(); }

    @Override
    public void waterlog() {
        if (this.isAir()) {
            this.blockData_ = Registry.BLOCK.getOrThrow(BukkitIDs.toKey(ID.of("water"))).createBlockData();
            return;
        }
        if (!(this.blockData_ instanceof Waterlogged waterlogged)) {
            return;
        }
        waterlogged.setWaterlogged(true);
        this.blockData_ = waterlogged;
    }

    @Override
    public ID id() {
        return BukkitIDs.from(blockData_.getMaterial().asBlockType().getKey());
    }
    public org.bukkit.Material getMaterial() { return this.blockData_.getMaterial(); }
}
