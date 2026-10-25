package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.api.World;
import java.util.Objects;

/** Paper-backed mutable biome data passed to the platform-neutral engine. */
public final class BukkitBiomeData implements BiomeData {

    private Biome biome;
    private final World world;
    private final int x;
    private final int y;
    private final int z;

    public BukkitBiomeData(org.bukkit.generator.WorldInfo worldInfo, org.bukkit.block.Biome biome, int x, int y, int z) {
        this.world = new BukkitWorldInfo(Objects.requireNonNull(worldInfo, "worldInfo"));
        this.biome = new BukkitBiome(Objects.requireNonNull(biome, "biome").getKey());
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public World world() { return world; }

    @Override
    public Biome get() { return biome; }

    @Override
    public void set(Biome biome) { this.biome = Objects.requireNonNull(biome, "biome"); }

    @Override
    public int getX() { return x; }

    @Override
    public int getY() { return y; }

    @Override
    public int getZ() { return z; }

    public org.bukkit.block.Biome getBukkitBiome() {
        return BukkitBiome.getBiomeRegistryAccess().get(BukkitIDs.toKey(biome.id()));
    }
}
