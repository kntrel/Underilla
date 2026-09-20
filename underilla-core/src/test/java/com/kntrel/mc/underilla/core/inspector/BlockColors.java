package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.ID;
import java.awt.Color;
import java.util.Map;

/** Explicit colors for blocks shown by the inspector. */
final class BlockColors {

    private static final Color UNKNOWN = new Color(0xE433A3);
    private static final Map<ID, Color> COLORS = Map.ofEntries(
            Map.entry(ID.of("minecraft:air"), new Color(0xD9F1FF)),
            Map.entry(ID.of("minecraft:cave_air"), new Color(0xD9F1FF)),
            Map.entry(ID.of("minecraft:void_air"), new Color(0xD9F1FF)),
            Map.entry(ID.of("minecraft:water"), new Color(0x3D75CC)),
            Map.entry(ID.of("minecraft:lava"), new Color(0xFF6A1A)),
            Map.entry(ID.of("minecraft:stone"), new Color(0x858585)),
            Map.entry(ID.of("minecraft:deepslate"), new Color(0x4C4D50)),
            Map.entry(ID.of("minecraft:bedrock"), new Color(0x303034)),
            Map.entry(ID.of("minecraft:dirt"), new Color(0x805432)),
            Map.entry(ID.of("minecraft:grass_block"), new Color(0x6FA449)),
            Map.entry(ID.of("minecraft:oak_log"), new Color(0x6B5135)),
            Map.entry(ID.of("minecraft:oak_leaves"), new Color(0x4F8A3A)),
            Map.entry(ID.of("minecraft:sand"), new Color(0xD9CC8F)),
            Map.entry(ID.of("minecraft:sandstone"), new Color(0xC9B775)),
            Map.entry(ID.of("minecraft:gravel"), new Color(0x9B9690)),
            Map.entry(ID.of("minecraft:clay"), new Color(0xA8ADB6)),
            Map.entry(ID.of("minecraft:snow_block"), new Color(0xEFF8FF)),
            Map.entry(ID.of("minecraft:ice"), new Color(0xA8D7EF)),
            Map.entry(ID.of("minecraft:granite"), new Color(0xA77E70)),
            Map.entry(ID.of("minecraft:diorite"), new Color(0xCECECB)),
            Map.entry(ID.of("minecraft:andesite"), new Color(0x91918D)),
            Map.entry(ID.of("minecraft:tuff"), new Color(0x6F756C)),
            Map.entry(ID.of("minecraft:calcite"), new Color(0xEEECE2)),
            Map.entry(ID.of("minecraft:dripstone_block"), new Color(0x8F725F))
    );

    private BlockColors() {}

    static Color get(ID id) {
        return COLORS.getOrDefault(id, UNKNOWN);
    }
}
