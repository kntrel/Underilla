package com.kntrel.mc.underilla.paper.io;

import com.jkantrell.nbt.io.NBTUtil;
import com.jkantrell.nbt.tag.CompoundTag;
import com.jkantrell.nbt.tag.StringTag;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.World;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.FileConfiguration;

/** Resolves reference metadata without loading the source world into the server. */
public final class ReferenceWorldMetadata {
    private ReferenceWorldMetadata() {}

    public static World read(FileConfiguration config, String section, Path regions) {
        Path regionDirectory = regions.toAbsolutePath().normalize();
        Path root = regionDirectory.getParent();
        while (root != null && !Files.isRegularFile(root.resolve("level.dat"))) {
            root = root.getParent();
        }
        CompoundTag data = new CompoundTag();
        if (root != null) {
            try {
                CompoundTag saved = (CompoundTag) NBTUtil.read(root.resolve("level.dat").toFile(), true).getTag();
                data = saved.getCompoundTag("Data");
                if (data == null) { throw new IllegalArgumentException("Missing Data compound in " + root.resolve("level.dat")); }
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not read reference world metadata at " + root, exception);
            }
        }
        Path inferredRoot = root == null ? conventionalRoot(regionDirectory) : root;
        String name = config.getString(section + ".name");
        if (name == null || name.isBlank()) { name = data.getString("LevelName"); }
        if (name == null || name.isBlank()) {
            if (inferredRoot == null || inferredRoot.getFileName() == null) {
                throw missing(section, "name");
            }
            name = inferredRoot.getFileName().toString();
        }
        String dimensionName = dimensionAt(inferredRoot, regionDirectory);
        if (dimensionName == null) { dimensionName = config.getString(section + ".dimension"); }
        if (dimensionName == null || dimensionName.isBlank()) { throw missing(section, "dimension"); }
        ID dimension = ID.of(dimensionName);

        CompoundTag generation = data.getCompoundTag("WorldGenSettings");
        CompoundTag inlineType = null;
        ID dimensionType = dimension;
        if (generation != null) {
            CompoundTag dimensions = generation.getCompoundTag("dimensions");
            CompoundTag selected = dimensions == null ? null : dimensions.getCompoundTag(dimension.toString());
            if (selected != null && selected.get("type") instanceof StringTag type) {
                dimensionType = ID.of(type.getValue());
            } else if (selected != null && selected.get("type") instanceof CompoundTag type) {
                inlineType = type;
            }
        }
        Integer minimumY = null;
        Integer maximumY = null;
        if (inlineType != null && inlineType.get("min_y") != null && inlineType.get("height") != null) {
            minimumY = inlineType.getInt("min_y");
            maximumY = Math.addExact(minimumY, inlineType.getInt("height"));
        } else {
            switch (dimensionType.toString()) {
                case "minecraft:overworld", "minecraft:overworld_caves" -> { minimumY = -64; maximumY = 320; }
                case "minecraft:the_nether", "minecraft:the_end" -> { minimumY = 0; maximumY = 256; }
                default -> { /* Custom external dimension types require explicit bounds. */ }
            }
        }
        if (config.isSet(section + ".minHeight")) { minimumY = config.getInt(section + ".minHeight"); }
        if (config.isSet(section + ".maxHeight")) { maximumY = config.getInt(section + ".maxHeight"); }
        if (minimumY == null) { throw missing(section, "minHeight"); }
        if (maximumY == null) { throw missing(section, "maxHeight"); }
        return World.of(name, dimension, minimumY, maximumY);
    }

    private static IllegalArgumentException missing(String section, String field) {
        return new IllegalArgumentException("Reference world metadata is unavailable; configure "
                + section + "." + field + " or provide the saved world metadata");
    }

    private static Path conventionalRoot(Path regions) {
        if (regions.getFileName() == null || !regions.getFileName().toString().equals("region")) { return null; }
        Path parent = regions.getParent();
        if (parent == null) { return null; }
        for (Path current = parent; current != null; current = current.getParent()) {
            if (current.getFileName() != null && current.getFileName().toString().equals("dimensions")) {
                return current.getParent();
            }
        }
        if (parent.getFileName() == null) { return parent; }
        String folder = parent.getFileName().toString();
        return folder.equals("DIM-1") || folder.equals("DIM1") ? parent.getParent() : parent;
    }

    private static String dimensionAt(Path root, Path regions) {
        if (root == null || !regions.startsWith(root)) { return null; }
        Path path = root.relativize(regions);
        if (path.toString().equals("region")) { return "minecraft:overworld"; }
        if (path.toString().replace('\\', '/').equals("DIM-1/region")) { return "minecraft:the_nether"; }
        if (path.toString().replace('\\', '/').equals("DIM1/region")) { return "minecraft:the_end"; }
        if (path.getNameCount() >= 4 && path.getName(0).toString().equals("dimensions")
                && path.getFileName().toString().equals("region")) {
            return path.getName(1) + ":" + path.subpath(2, path.getNameCount() - 1).toString().replace('\\', '/');
        }
        return null;
    }
}
