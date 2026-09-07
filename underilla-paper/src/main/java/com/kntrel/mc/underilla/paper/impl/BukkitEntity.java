package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.Entity;
import java.util.Objects;

/** Mutable core view over a live Bukkit entity. */
public final class BukkitEntity implements Entity {

    private final org.bukkit.entity.Entity entity;

    public BukkitEntity(org.bukkit.entity.Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    public org.bukkit.entity.Entity getEntity() { return entity; }

    @Override
    public String getType() { return entity.getType().toString(); }

    @Override
    public void remove() { entity.remove(); }
}
