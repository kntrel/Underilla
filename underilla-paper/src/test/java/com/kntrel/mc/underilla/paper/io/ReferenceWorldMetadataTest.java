package com.kntrel.mc.underilla.paper.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jkantrell.nbt.io.NBTUtil;
import com.jkantrell.nbt.tag.CompoundTag;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.World;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.paper.impl.BukkitWorldReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceWorldMetadataTest {

    @Test
    void readsSavedMetadataAndRetainsItThroughThePlanLocalReaderCache(@TempDir Path root) throws Exception {
        Path regions = Files.createDirectories(root.resolve("dimensions/minecraft/the_nether/region"));
        writeLevel(root, "Saved source", 1234, "minecraft:the_nether", null);

        World world = ReferenceWorldMetadata.read(new YamlConfiguration(), "surfaceWorld", regions);

        assertEquals("Saved source", world.name());
        assertEquals(ID.of("minecraft:the_nether"), world.dimension());
        assertEquals(1234L, world.seed());
        assertEquals(0, world.minHeight());
        assertEquals(256, world.maxHeight());
        BukkitWorldReader reader = new BukkitWorldReader(regions.toFile(), null, 1, world);
        World cached = reader.withChunkCache(new ChunkCache(1));
        assertEquals(world.name(), cached.name());
        assertEquals(world.dimension(), cached.dimension());
        assertEquals(world.seed(), cached.seed());
        assertEquals(world.minHeight(), cached.minHeight());
        assertEquals(world.maxHeight(), cached.maxHeight());
    }

    @Test
    void readsInlineCustomDimensionBounds(@TempDir Path root) throws IOException {
        Path regions = Files.createDirectories(root.resolve("dimensions/example/deep/moon/region"));
        CompoundTag dimensionType = new CompoundTag();
        dimensionType.putInt("min_y", -128);
        dimensionType.putInt("height", 512);
        writeLevel(root, "Moon", 99, "example:deep/moon", dimensionType);

        World world = ReferenceWorldMetadata.read(new YamlConfiguration(), "surfaceWorld", regions);

        assertEquals(ID.of("example:deep/moon"), world.dimension());
        assertEquals(99L, world.seed());
        assertEquals(-128, world.minHeight());
        assertEquals(384, world.maxHeight());
    }

    @Test
    void readsLegacySeedsFromLevelDat(@TempDir Path root) throws IOException {
        Path regions = Files.createDirectories(root.resolve("region"));
        CompoundTag data = new CompoundTag();
        data.putString("LevelName", "Legacy source");
        data.putLong("RandomSeed", -987654321L);
        CompoundTag level = new CompoundTag();
        level.put("Data", data);
        NBTUtil.write(level, root.resolve("level.dat").toFile(), true);

        World world = ReferenceWorldMetadata.read(new YamlConfiguration(), "surfaceWorld", regions);

        assertEquals(-987654321L, world.seed());
    }

    @Test
    void acceptsExplicitMetadataForRegionOnlyExports(@TempDir Path root) throws IOException {
        Path regions = Files.createDirectories(root.resolve("exported-chunks"));
        YamlConfiguration config = new YamlConfiguration();
        config.set("surfaceWorld.name", "Export");
        config.set("surfaceWorld.dimension", "example:moon");
        config.set("surfaceWorld.minHeight", -32);
        config.set("surfaceWorld.maxHeight", 96);

        World world = ReferenceWorldMetadata.read(config, "surfaceWorld", regions);

        assertEquals("Export", world.name());
        assertEquals(0L, world.seed());
        assertEquals(ID.of("example:moon"), world.dimension());
        assertEquals(-32, world.minHeight());
        assertEquals(96, world.maxHeight());
    }

    @Test
    void vanillaRegionOnlyExportsDoNotRequireLevelDat(@TempDir Path root) throws IOException {
        Path regions = Files.createDirectories(root.resolve("region"));
        YamlConfiguration config = new YamlConfiguration();
        config.set("surfaceWorld.dimension", "ignored invalid dimension");

        World world = ReferenceWorldMetadata.read(config, "surfaceWorld", regions);

        assertEquals(root.getFileName().toString(), world.name());
        assertEquals(ID.of("minecraft:overworld"), world.dimension());
        assertEquals(-64, world.minHeight());
        assertEquals(320, world.maxHeight());
    }

    @Test
    void reportsMissingCustomDimensionBounds(@TempDir Path root) throws IOException {
        Path regions = Files.createDirectories(root.resolve("exported-chunks"));
        YamlConfiguration config = new YamlConfiguration();
        config.set("surfaceWorld.dimension", "example:moon");
        config.set("surfaceWorld.name", "Moon");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ReferenceWorldMetadata.read(config, "surfaceWorld", regions));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("surfaceWorld.minHeight"));
    }

    private static void writeLevel(Path root, String name, long seed, String dimension, CompoundTag inlineType)
            throws IOException {
        CompoundTag generation = new CompoundTag();
        generation.putLong("seed", seed);
        CompoundTag entry = new CompoundTag();
        if (inlineType == null) {
            entry.putString("type", dimension);
        } else {
            entry.put("type", inlineType);
        }
        CompoundTag dimensions = new CompoundTag();
        dimensions.put(dimension, entry);
        generation.put("dimensions", dimensions);
        CompoundTag data = new CompoundTag();
        data.putString("LevelName", name);
        data.put("WorldGenSettings", generation);
        CompoundTag level = new CompoundTag();
        level.put("Data", data);
        NBTUtil.write(level, root.resolve("level.dat").toFile(), true);
    }
}
