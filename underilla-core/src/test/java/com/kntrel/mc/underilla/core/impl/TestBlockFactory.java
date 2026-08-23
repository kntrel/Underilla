package com.kntrel.mc.underilla.core.impl;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Small block registry used by the in-memory test API. */
public final class TestBlockFactory implements BlockFactory {

    private static final Set<String> AIR_BLOCKS = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final Set<String> LIQUID_BLOCKS = Set.of(
            "minecraft:water", "minecraft:lava", "minecraft:bubble_column");
    private static final Set<String> NON_SOLID_BLOCKS = Set.of(
            "minecraft:allium",
            "minecraft:amethyst_cluster",
            "minecraft:azure_bluet",
            "minecraft:blue_orchid",
            "minecraft:brown_mushroom",
            "minecraft:bush",
            "minecraft:cornflower",
            "minecraft:dandelion",
            "minecraft:dead_bush",
            "minecraft:fern",
            "minecraft:firefly_bush",
            "minecraft:glow_lichen",
            "minecraft:kelp",
            "minecraft:kelp_plant",
            "minecraft:large_amethyst_bud",
            "minecraft:large_fern",
            "minecraft:leaf_litter",
            "minecraft:lilac",
            "minecraft:medium_amethyst_bud",
            "minecraft:orange_tulip",
            "minecraft:oxeye_daisy",
            "minecraft:peony",
            "minecraft:pink_tulip",
            "minecraft:poppy",
            "minecraft:red_tulip",
            "minecraft:rose_bush",
            "minecraft:seagrass",
            "minecraft:short_grass",
            "minecraft:small_amethyst_bud",
            "minecraft:snow",
            "minecraft:sugar_cane",
            "minecraft:sunflower",
            "minecraft:tall_grass",
            "minecraft:tall_seagrass",
            "minecraft:vine",
            "minecraft:white_tulip",
            "minecraft:wildflowers");

    private final TestBlock air;
    private final Map<String, Block> blocks = new HashMap<>();

    public TestBlockFactory(TestBlock air, TestBlock... knownBlocks) {
        this.air = Objects.requireNonNull(air, "air");
        register(air);
        for (TestBlock block : knownBlocks) {
            register(block);
        }
    }

    public TestBlockFactory register(TestBlock block) {
        blocks.put(block.getName(), block);
        return this;
    }

    @Override
    public Block air() { return air; }

    @Override
    public Block create(String name) {
        return blocks.computeIfAbsent(name, TestBlockFactory::createBlock);
    }

    private static TestBlock createBlock(String name) {
        if (AIR_BLOCKS.contains(name)) {
            return TestBlock.air(name);
        }
        if (LIQUID_BLOCKS.contains(name)) {
            return TestBlock.liquid(name);
        }
        if (NON_SOLID_BLOCKS.contains(name)) {
            return TestBlock.nonSolid(name);
        }
        return TestBlock.solid(name);
    }
}
