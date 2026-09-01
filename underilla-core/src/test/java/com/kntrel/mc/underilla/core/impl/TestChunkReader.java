package com.kntrel.mc.underilla.core.impl;

import com.jkantrell.mca.Chunk;
import com.jkantrell.nbt.tag.CompoundTag;
import com.jkantrell.nbt.tag.StringTag;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reads real Anvil chunk tags into platform-neutral test API values. */
public final class TestChunkReader extends ChunkReader {

    private final TestBlockFactory blocks;

    public TestChunkReader(Chunk chunk, TestBlockFactory blocks) {
        this(chunk, List.of(), blocks);
    }

    public TestChunkReader(Chunk chunk, List<EntityView> entities, TestBlockFactory blocks) {
        super(chunk, entities);
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    @Override
    public Optional<Block> blockFromTag(CompoundTag tag) {
        if (tag == null) {
            return Optional.empty();
        }
        String name = tag.getString("Name");
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(blocks.create(name));
    }

    @Override
    public Optional<Block> blockFromTag(CompoundTag tag, CompoundTag entityTag) {
        return blockFromTag(tag);
    }

    @Override
    public Optional<Biome> biomeFromTag(StringTag tag) {
        return tag == null ? Optional.empty() : Optional.of(new TestBiome(tag.getValue()));
    }
}
