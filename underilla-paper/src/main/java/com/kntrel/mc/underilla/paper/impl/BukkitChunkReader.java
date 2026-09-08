package com.kntrel.mc.underilla.paper.impl;

import com.jkantrell.mca.Chunk;
import com.jkantrell.nbt.tag.CompoundTag;
import com.jkantrell.nbt.tag.StringTag;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.EntityView;
import com.kntrel.mc.underilla.core.reader.TagInterpreter;
import java.util.List;
import java.util.Optional;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BukkitChunkReader extends ChunkReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(BukkitChunkReader.class);

    // CONSTRUCTORS
    public BukkitChunkReader(Chunk chunk) { super(chunk); }
    public BukkitChunkReader(Chunk chunk, List<EntityView> entities) { super(chunk, entities); }


    // IMPLEMENTATION
    @Override
    protected Optional<Block> decodeBlockFromTag(CompoundTag tag) {
        String name = tag.getString("Name");
        if (name == null) {
            return Optional.empty();
        }
        ID id;
        try {
            id = ID.of(name);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        BlockType type = Registry.BLOCK.get(BukkitIDs.toKey(id));
        if (type == null) {
            return Optional.empty();
        }

        CompoundTag properties = tag != null ? tag.getCompoundTag("Properties") : null;
        if (properties == null) {
            return Optional.of(new BukkitBlock(type.createBlockData()));
        }

        // IllegalArgumentException might be thrown if block data is not compatible with current version of Minecraft
        // In such case, return plain block with no data
        try {
            String dataString = TagInterpreter.COMPOUND.interpretBlockDataString(properties);
            // if (dataString != null && dataString.length() > 90) {
            // LOGGER.debug("{}", dataString);
            // }
            if (id.equals(ID.of("vine"))) {
                dataString = removeVineDownProperty(dataString);
            }
            return Optional.of(new BukkitBlock(type.createBlockData(dataString)));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Failed to create block data {}", id, e);
            return Optional.of(new BukkitBlock(type.createBlockData()));
        }
    }

    // From minecraft:vine[west=false,east=false,up=false,south=false,down=false,north=true]
    // To minecraft:vine[west=false,east=false,up=false,south=false,north=true]
    private String removeVineDownProperty(String dataString) { return dataString.replace(",down=false", ""); }

    // [21:29:47 INFO]: [Underilla] [STDOUT] Loaded Block Entities:
    // {"type":"ListTag","value":{"type":"CompoundTag","list":[{"MaxNearbyEntities":{"type":"ShortTag","value":6},
    // "RequiredPlayerRange":{"type":"ShortTag","value":16},"SpawnCount":{"type":"ShortTag","value":4},
    // "SpawnData":{"type":"CompoundTag","value":{"entity":{"type":"CompoundTag","value":{"id":{"type":"StringTag","value":"minecraft:cave_spider"}}}}},
    // "MaxSpawnDelay":{"type":"ShortTag","value":800},"Delay":{"type":"ShortTag","value":20},"keepPacked":{"type":"ByteTag","value":0},
    // "x":{"type":"IntTag","value":5332},"y":{"type":"IntTag","value":-27},"z":{"type":"IntTag","value":5988},
    // "id":{"type":"StringTag","value":"minecraft:mob_spawner"},"SpawnRange":{"type":"ShortTag","value":4},
    // "MinSpawnDelay":{"type":"ShortTag","value":200},"SpawnPotentials":{"type":"ListTag","value":{"type":"EndTag","list":[]}}}]}}
    @Override
    public Optional<Block> blockFromTag(CompoundTag tag, CompoundTag blockEntity) {
        Optional<Block> block = blockFromTag(tag);
        if (block.isPresent() && blockEntity != null) {
            // System.out.println("Loaded Block Entities: " + blockEntity);
            // if it's a spawner or a chest
            if (blockEntity.getString("id").equals("minecraft:mob_spawner")) {
                try {
                    // LOGGER.debug("Interesting Spawner: {}", blockEntity);
                    String spawnedType = blockEntity.getCompoundTag("SpawnData").getCompoundTag("entity").getString("id");
                    // System.out.println("Spawner Type: " + spawnedType);
                    if (block.get() instanceof BukkitBlock bukkitBlock) {
                        // bukkitBlock.setBlockData(Material.SPAWNER.createBlockData());
                        bukkitBlock.setSpawnedType(spawnedType);
                        // LOGGER.debug("blockFromTag: {}", bukkitBlock.getSpawnedType());
                    }

                    // } else if (blockEntity.getString("id").equals("minecraft:chest")) {
                    // System.out.println("Interesting Chest: " + blockEntity);
                } catch (Exception e) {
                    LOGGER.warn("Failed to set the type of a spawner: {}", blockEntity, e);
                }

            } else if (blockEntity.getString("id").equals("minecraft:chest")) {

            }
            // System.out.println("Loaded Block Entities: " + blockEntity.getString("id") + ":\n" + blockEntity);
        }
        return block;
    }


    @Override
    public Optional<Biome> biomeFromTag(StringTag tag) { return Optional.of(new BukkitBiome(tag.getValue())); }
}
