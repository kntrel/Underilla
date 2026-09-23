package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.ID;
import java.awt.Color;
import java.util.Map;

/** Explicit overlay colors for biomes shown by the inspector. */
final class BiomeColors {

    private static final Color UNKNOWN = new Color(0xE433A3);
    private static final Map<ID, Color> COLORS = Map.ofEntries(
            Map.entry(ID.of("minecraft:plains"), new Color(0x8DB360)),
            Map.entry(ID.of("minecraft:sunflower_plains"), new Color(0xB5DB88)),
            Map.entry(ID.of("minecraft:forest"), new Color(0x056621)),
            Map.entry(ID.of("minecraft:flower_forest"), new Color(0x2D8E49)),
            Map.entry(ID.of("minecraft:birch_forest"), new Color(0x307444)),
            Map.entry(ID.of("minecraft:dark_forest"), new Color(0x40511A)),
            Map.entry(ID.of("minecraft:taiga"), new Color(0x0B6659)),
            Map.entry(ID.of("minecraft:snowy_taiga"), new Color(0x31554A)),
            Map.entry(ID.of("minecraft:old_growth_pine_taiga"), new Color(0x596651)),
            Map.entry(ID.of("minecraft:old_growth_spruce_taiga"), new Color(0x818E79)),
            Map.entry(ID.of("minecraft:desert"), new Color(0xFA9418)),
            Map.entry(ID.of("minecraft:savanna"), new Color(0xBDB25F)),
            Map.entry(ID.of("minecraft:savanna_plateau"), new Color(0xA79D64)),
            Map.entry(ID.of("minecraft:jungle"), new Color(0x537B09)),
            Map.entry(ID.of("minecraft:sparse_jungle"), new Color(0x628B17)),
            Map.entry(ID.of("minecraft:bamboo_jungle"), new Color(0x768E14)),
            Map.entry(ID.of("minecraft:swamp"), new Color(0x07F9B2)),
            Map.entry(ID.of("minecraft:mangrove_swamp"), new Color(0x2B7A4B)),
            Map.entry(ID.of("minecraft:badlands"), new Color(0xD94515)),
            Map.entry(ID.of("minecraft:eroded_badlands"), new Color(0xFF6D3D)),
            Map.entry(ID.of("minecraft:wooded_badlands"), new Color(0xB09765)),
            Map.entry(ID.of("minecraft:windswept_hills"), new Color(0x606060)),
            Map.entry(ID.of("minecraft:meadow"), new Color(0x83BB6D)),
            Map.entry(ID.of("minecraft:grove"), new Color(0x8AB689)),
            Map.entry(ID.of("minecraft:snowy_slopes"), new Color(0xD1D1D1)),
            Map.entry(ID.of("minecraft:frozen_peaks"), new Color(0xA2A2A2)),
            Map.entry(ID.of("minecraft:jagged_peaks"), new Color(0xC0C0C0)),
            Map.entry(ID.of("minecraft:stony_peaks"), new Color(0x888888)),
            Map.entry(ID.of("minecraft:river"), new Color(0x0000FF)),
            Map.entry(ID.of("minecraft:frozen_river"), new Color(0xA0A0FF)),
            Map.entry(ID.of("minecraft:beach"), new Color(0xFADE55)),
            Map.entry(ID.of("minecraft:snowy_beach"), new Color(0xFAFAFA)),
            Map.entry(ID.of("minecraft:stony_shore"), new Color(0xA2A284)),
            Map.entry(ID.of("minecraft:ocean"), new Color(0x000070)),
            Map.entry(ID.of("minecraft:deep_ocean"), new Color(0x000030)),
            Map.entry(ID.of("minecraft:warm_ocean"), new Color(0x000172)),
            Map.entry(ID.of("minecraft:lukewarm_ocean"), new Color(0x000090)),
            Map.entry(ID.of("minecraft:cold_ocean"), new Color(0x202070)),
            Map.entry(ID.of("minecraft:frozen_ocean"), new Color(0x7070D6)),
            Map.entry(ID.of("minecraft:deep_dark"), new Color(0x202020)),
            Map.entry(ID.of("minecraft:dripstone_caves"), new Color(0x8B6A4A)),
            Map.entry(ID.of("minecraft:lush_caves"), new Color(0x4E8A3A)),
            Map.entry(ID.of("minecraft:the_end"), new Color(0x8080A0)),
            Map.entry(ID.of("minecraft:nether_wastes"), new Color(0x572526)),
            Map.entry(ID.of("minecraft:crimson_forest"), new Color(0xDD0808)),
            Map.entry(ID.of("minecraft:warped_forest"), new Color(0x49907B)),
            Map.entry(ID.of("minecraft:soul_sand_valley"), new Color(0x5B4A4C)),
            Map.entry(ID.of("minecraft:basalt_deltas"), new Color(0x403636))
    );

    private BiomeColors() {}

    static Color get(ID id) {
        return COLORS.getOrDefault(id, UNKNOWN);
    }
}
