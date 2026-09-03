package com.kntrel.mc.underilla.paper.impl;

import com.jkantrell.nbt.io.NBTSerializer;
import com.jkantrell.nbt.io.NamedTag;
import com.jkantrell.nbt.tag.CompoundTag;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.reader.EntityView;
import com.kntrel.mc.underilla.core.vector.VectorIterable;
import com.kntrel.mc.underilla.paper.Underilla;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.LimitedRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BukkitRegionChunkData implements ChunkData {

    private static final Logger LOGGER = LoggerFactory.getLogger(BukkitRegionChunkData.class);
    // FIELDS
    private final LimitedRegion region;
    private final int minHeight_, maxHeight_, chunkX_, chunkZ_, absX_, absZ_;


    // CONSTRUCTORS
    public BukkitRegionChunkData(LimitedRegion region, int chunkX, int chunkZ, int minHeight, int maxHeight) {
        this.region = region;
        this.minHeight_ = minHeight;
        this.maxHeight_ = maxHeight;
        this.chunkX_ = chunkX;
        this.chunkZ_ = chunkZ;
        this.absX_ = this.chunkX_ * 16;
        this.absZ_ = this.chunkZ_ * 16;
    }


    // GETTERS
    public LimitedRegion getRegion() { return this.region; }


    // IMPLEMENTATIONS
    @Override
    public int getMaxHeight() { return this.maxHeight_; }
    @Override
    public int getMinHeight() { return this.minHeight_; }
    public int getChunkX() { return this.chunkX_; }
    public int getChunkZ() { return this.chunkZ_; }
    @Override
    public Block getBlock(int x, int y, int z) {
        // TODO also save state of block if needed (for structure chests).
        // this.region.getBlockState(this.absX_ + x, y, this.absZ_ + z);
        BlockData d = this.region.getBlockData(this.absX_ + x, y, this.absZ_ + z);
        return new BukkitBlock(d);
    }
    @Override
    public Biome getBiome(int x, int y, int z) {
        org.bukkit.block.Biome b = this.region.getBiome(this.absX_ + x, y, this.absZ_ + z);
        return new BukkitBiome(b.key().asString());
    }
    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Block block) {
        new VectorIterable(xMin, xMax, yMin, yMax, zMin, zMax).forEach(v -> this.setBlock(v, block));
    }
    @Override
    public void setBlock(int x, int y, int z, Block block) {
        if (!(block instanceof BukkitBlock bukkitBlock)) {
            return;
        }
        this.region.setBlockData(this.absX_ + x, y, this.absZ_ + z, bukkitBlock.getBlockData());
        // TODO nexts lines are never called
        if (bukkitBlock.getSpawnedType().isPresent()) {
            if (region.getWorld().getBlockAt(this.absX_ + x, y,
                    this.absZ_ + z) instanceof org.bukkit.block.CreatureSpawner creatureSpawner) {
                creatureSpawner.setSpawnedType(bukkitBlock.getSpawnedType().get());
                creatureSpawner.update();
                LOGGER.info("Set spawner type to {}", bukkitBlock.getSpawnedType().get());
            }
        }
    }
    @Override
    public void setBiome(int x, int y, int z, Biome underillaBiome) {
        if (!(underillaBiome instanceof BukkitBiome bukkitBiome)) {
            return;
        }
        org.bukkit.block.Biome biome = bukkitBiome.getBiome();
        // Final transformation that can be override by other plugins
        if (Underilla.getInstance().hasEndBiomeTransformer()) {
            biome = Underilla.getInstance().getEndBiomeTransformer().apply(biome);
        }
        Underilla.getUnderillaConfig().getSelector().getWorld().setBiome(this.absX_ + x, y, this.absZ_ + z, biome);
    }
    @Override
    @SuppressWarnings("deprecation")
    public void addEntity(EntityView entity) {
        try {
            org.bukkit.entity.Entity bukkitEntity = Bukkit.getUnsafe().deserializeEntity(
                    serialize(entity), this.region.getWorld(), false, true);
            this.region.addEntity(bukkitEntity);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to deserialize a reference entity in chunk {}, {}",
                    getChunkX(), getChunkZ(), exception);
        }
    }

    private static byte[] serialize(EntityView entityView) throws IOException {
        CompoundTag entity = entityView.tag();
        entity.putInt("DataVersion", entityView.dataVersion());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream compressed = new GZIPOutputStream(output)) {
            new NBTSerializer(false).toStream(new NamedTag(null, entity), compressed);
        }
        return output.toByteArray();
    }
}
