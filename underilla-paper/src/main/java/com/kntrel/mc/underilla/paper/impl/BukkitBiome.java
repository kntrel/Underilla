package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.ID;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.TagKey;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

public class BukkitBiome implements Biome {

    public static final BukkitBiome DEFAULT = new BukkitBiome(ID.of("plains"));

    // FIELDS
    private final ID id;


    // CONSTRUCTORS
    public BukkitBiome(String name) { this(ID.of(name)); }
    public BukkitBiome(ID id) { this.id = id; }
    public BukkitBiome(NamespacedKey key) { this(BukkitIDs.from(key)); }


    // GETTERS
    public org.bukkit.block.Biome getBiome() { return getBiomeRegistryAccess().get(BukkitIDs.toKey(id)); }

    public static Registry<org.bukkit.block.Biome> getBiomeRegistryAccess() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
    }

    public static Stream<org.bukkit.block.Biome> getAllBiomesStream() { return getBiomeRegistryAccess().stream(); }
    public static Collection<org.bukkit.block.Biome> getAllBiomes() {
        return getBiomeRegistryAccess().stream().collect(Collectors.toSet());
    }
    public static List<org.bukkit.block.Biome> getAllBiomesList() { return getBiomeRegistryAccess().stream().toList(); }

    public static Collection<ID> getAllBiomeIDs() {
        return getBiomeRegistryAccess().keyStream().map(BukkitIDs::from).toList();
    }
    public static Collection<org.bukkit.block.Biome> getAllBiomesOfTag(String tag) {
        return getBiomeRegistryAccess().getTagValues(TagKey.create(io.papermc.paper.registry.RegistryKey.BIOME, tag));
    }


    // IMPLEMENTATIONS
    @Override
    public ID id() { return id; }
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        if (!(o instanceof BukkitBiome bukkitBiome)) {
            return false;
        }
        return this.id.equals(bukkitBiome.id);
    }
    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "BukkitBiome{" + id + '}'; }
}
