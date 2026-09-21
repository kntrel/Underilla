package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.Entity;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.Objects;
import org.bukkit.Registry;

/** Mutable core view over a live Bukkit entity. */
public final class BukkitEntity implements Entity {

    private final org.bukkit.entity.Entity entity;

    public BukkitEntity(org.bukkit.entity.Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    public org.bukkit.entity.Entity getEntity() { return entity; }

    @Override
    public ID id() { return BukkitIDs.from(Registry.ENTITY_TYPE.getKeyOrThrow(entity.getType())); }

    @Override
    public void remove() { entity.remove(); }
}
