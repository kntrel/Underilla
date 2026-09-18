package com.kntrel.mc.underilla.paper.io;

import java.util.List;
import org.jspecify.annotations.NonNull;

public class Tools {
    private Tools() {}

    /**
     * Normalize the name to the format minecraft:name
     * Any bukkit name will be converted to minecraft name.
     * e.g. "minecraft:plains" or "PLAINS" will be returned as "minecraft:plains"
     * 
     * @param name the name
     * @return a normalized name
     */
    public static @NonNull String normalizeName(@NonNull String name) {
        name = name.toLowerCase();
        if (!name.contains(":")) {
            name = "minecraft:" + name;
        }
        return name;
    }
    /**
     * Normalize the name to the format minecraft:name
     * Any bukkit name will be converted to minecraft name.
     * e.g. "minecraft:plains" or "PLAINS" will be returned as "minecraft:plains"
     * 
     * @param nameList a list of names
     * @return a list of normalized names
     */
    public static @NonNull List<String> normalizeNameList(@NonNull List<String> nameList) {
        return nameList.stream().map(Tools::normalizeName).toList();
    }
}
