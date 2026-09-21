package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.ID;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;

public final class BukkitIDs {

    private BukkitIDs() {}

    public static ID from(Key key) {
        return ID.of(key.namespace(), key.value());
    }

    public static NamespacedKey toKey(ID id) {
        return new NamespacedKey(id.namespace(), id.value());
    }
}
