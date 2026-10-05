package com.kntrel.mc.underilla.core.inspector;

import com.jkantrell.nbt.io.NBTUtil;
import com.jkantrell.nbt.tag.CompoundTag;
import com.jkantrell.nbt.tag.IntArrayTag;
import com.jkantrell.nbt.tag.IntTag;
import com.jkantrell.nbt.tag.ListTag;
import com.jkantrell.nbt.tag.StringTag;
import com.jkantrell.nbt.tag.Tag;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persists rendering captures beneath an inspection output directory's {@code captures} folder.
 * Stores IDs only, not full block states; recovered slices must not be used to regenerate terrain.
 *
 * <p>Operations perform synchronous I/O. The caller owns scheduling and must finish recovery before
 * accepting new captures. Use one store instance per directory; concurrent processes are not supported.
 * Files are committed by atomic rename (required of the filesystem). This prevents partial records,
 * but does not guarantee durability across power loss or coordinate saves with Minecraft.</p>
 */
public final class InspectorPersister {

    private static final int VERSION = 1;
    private static final int CHUNK_SIZE = GenerationConstants.CHUNK_SIZE;

    private final Path directory;
    private final InspectionRegion region;
    private final List<InspectionStage> stages;
    private final BlockFactory blocks;

    /**
     * Opens or creates a store. Identity and settings fingerprint must remain stable across restarts,
     * and change when the world or generation inputs change. Incompatible state is rejected, never erased.
     */
    public InspectorPersister(Path output, String worldIdentity, String settingsFingerprint,
                              InspectionRegion region, Set<InspectionStage> stages, BlockFactory blocks) throws IOException {
        this.directory = Objects.requireNonNull(output, "output").toAbsolutePath().normalize().resolve("captures");
        this.region = Objects.requireNonNull(region, "region");
        this.stages = Set.copyOf(stages).stream().sorted(Comparator.comparingInt(InspectionStage::order)).toList();
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        if (this.stages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }
        if (Objects.requireNonNull(worldIdentity, "worldIdentity").isBlank()
                || Objects.requireNonNull(settingsFingerprint, "settingsFingerprint").isBlank()) {
            throw new IllegalArgumentException("World identity and settings fingerprint must not be blank");
        }
        Math.multiplyExact(region.startChunk(), CHUNK_SIZE);
        Math.multiplyExact(Math.addExact(region.startChunk(), region.chunkLength()), CHUNK_SIZE);
        Math.multiplyExact(CHUNK_SIZE, Math.subtractExact(region.maximumY(), region.minimumY()));
        CompoundTag manifest = new CompoundTag();
        manifest.putInt("version", VERSION);
        manifest.putString("world", worldIdentity);
        manifest.putString("settings", settingsFingerprint);
        manifest.putString("axis", region.axis().name());
        manifest.putInt("coordinate", region.coordinate());
        manifest.putInt("startChunk", region.startChunk());
        manifest.putInt("lengthChunks", region.chunkLength());
        manifest.putInt("minimumY", region.minimumY());
        manifest.putInt("maximumY", region.maximumY());
        ListTag<StringTag> stageNames = new ListTag<>(StringTag.class);
        this.stages.forEach(stage -> stageNames.addString(stage.phase().name() + "/" + stage.state().name()));
        manifest.put("stages", stageNames);

        Files.createDirectories(directory);
        Path manifestPath = directory.resolve("manifest.nbt");
        if (Files.exists(manifestPath)) {
            if (!manifest.equals(read(manifestPath))) {
                throw new IOException("Incompatible inspection state in " + directory + ": world, settings, region, stages or format version differ; choose another output directory");
            }
        } else {
            try (var files = Files.walk(directory)) {
                if (files.anyMatch(path -> path.getFileName().toString().endsWith(".nbt"))) {
                    throw new IOException("Inspection captures exist without a manifest in " + directory);
                }
            }
            write(manifestPath, manifest);
        }
    }

    /** Saves one complete chunk tile. Repeated saves replace the same stage/chunk record atomically. */
    public synchronized void save(InspectionStage stage, int chunkX, int chunkZ, WorldSlice slice) throws IOException {
        Objects.requireNonNull(slice, "slice");
        validatePosition(stage, chunkX, chunkZ);
        int horizontal = region.axis() == InspectionRegion.Axis.Z ? chunkX : chunkZ;
        if (       slice.getOffsetX()  != Math.multiplyExact(horizontal, CHUNK_SIZE)
                || slice.getOffsetY()  != region.minimumY()
                || slice.getWidth()    != CHUNK_SIZE
                || slice.getHeight()   != region.maximumY() - region.minimumY()
        ) {
            throw new IllegalArgumentException("Capture dimensions or origin do not match the inspection region");
        }
        CompoundTag tile = new CompoundTag();
        tile.putInt("version", VERSION);
        tile.putString("phase", stage.phase().name());
        tile.putString("state", stage.state().name());
        tile.putInt("chunkX", chunkX);
        tile.putInt("chunkZ", chunkZ);
        tile.putInt("offsetHorizontal", slice.getOffsetX());
        tile.putInt("offsetY", slice.getOffsetY());
        tile.putInt("width", slice.getWidth());
        tile.putInt("height", slice.getHeight());
        writeCells(tile, "blocks", slice.getWidth(), slice.getHeight(), (x, y) -> {
            Block block = slice.getBlock(x, y);
            return block == null ? null : block.id();
        });
        writeCells(tile, "biomes", slice.getBiomeWidth(), slice.getBiomeHeight(), (x, y) -> {
            var biome = slice.getBiome(x, y);
            return biome == null ? null : biome.id();
        });
        write(path(stage, chunkX, chunkZ), tile);
    }

    /**
     * Loads committed tiles in stage/chunk order. Missing tiles and unfinished temporary files are ignored.
     * Invalid committed files fail recovery with their path; they are never counted as received captures.
     * The factory recreates blocks from IDs; properties beyond those IDs are not preserved.
     */
    public synchronized List<Capture> load() throws IOException {
        List<Capture> captures = new ArrayList<>();
        int fixedChunk = Math.floorDiv(region.coordinate(), CHUNK_SIZE);
        for (InspectionStage stage : stages) {
            for (int i = 0; i < region.chunkLength(); i++) {
                int horizontal = Math.addExact(region.startChunk(), i);
                int chunkX = region.axis() == InspectionRegion.Axis.Z ? horizontal : fixedChunk;
                int chunkZ = region.axis() == InspectionRegion.Axis.Z ? fixedChunk : horizontal;
                Path file = path(stage, chunkX, chunkZ);
                if (!Files.exists(file)) {
                    continue;
                }
                try {
                    CompoundTag tile = read(file);
                    int height = region.maximumY() - region.minimumY();
                    int offset = Math.multiplyExact(horizontal, CHUNK_SIZE);
                    expect(tile, "version", VERSION);
                    expect(tile, "chunkX", chunkX);
                    expect(tile, "chunkZ", chunkZ);
                    expect(tile, "offsetHorizontal", offset);
                    expect(tile, "offsetY", region.minimumY());
                    expect(tile, "width", CHUNK_SIZE);
                    expect(tile, "height", height);
                    if (!required(tile, "phase", StringTag.class).getValue().equals(stage.phase().name())
                            || !required(tile, "state", StringTag.class).getValue().equals(stage.state().name())) {
                        throw new IOException("Capture stage does not match its path");
                    }
                    WorldSlice slice = new WorldSlice(offset, region.minimumY(), CHUNK_SIZE, height);
                    readCells(tile, "blocks", slice, false, blocks);
                    readCells(tile, "biomes", slice, true, blocks);
                    captures.add(new Capture(stage, chunkX, chunkZ, slice.freeze()));
                } catch (IOException | RuntimeException exception) {
                    throw new IOException("Could not restore inspection capture " + file, exception);
                }
            }
        }
        return List.copyOf(captures);
    }

    /** One recovered tile, ready for the inspector's normal frame assembly path. */
    public record Capture(InspectionStage stage, int chunkX, int chunkZ, WorldSlice slice) {}

    private void validatePosition(InspectionStage stage, int chunkX, int chunkZ) {
        if (!stages.contains(Objects.requireNonNull(stage, "stage")) || !region.contains(chunkX, chunkZ)) {
            throw new IllegalArgumentException("Capture stage or chunk is outside this inspection");
        }
    }

    private Path path(InspectionStage stage, int chunkX, int chunkZ) {
        return directory.resolve(stage.phase().name().toLowerCase(Locale.ROOT))
                .resolve(stage.state().name().toLowerCase(Locale.ROOT)).resolve(chunkX + "_" + chunkZ + ".nbt");
    }

    private static void writeCells(CompoundTag tile, String key, int width, int height, CellIdSource ids) {
        Map<ID, Integer> palette = new LinkedHashMap<>();
        int[] cells = new int[Math.multiplyExact(width, height)];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Zero means absent, including for biomes; air is an ordinary palette entry.
                ID id = ids.get(x, y);
                cells[x * height + y] = id == null ? 0 : palette.computeIfAbsent(id, _ -> palette.size() + 1);
            }
        }
        ListTag<StringTag> names = new ListTag<>(StringTag.class);
        palette.keySet().forEach(id -> names.addString(id.toString()));
        tile.put(key + "Palette", names);
        tile.putIntArray(key, cells);
    }

    @FunctionalInterface
    private interface CellIdSource {
        ID get(int x, int y);
    }

    private static void readCells(CompoundTag tile, String key, WorldSlice slice, boolean biomes,
            BlockFactory blocks) throws IOException {
        var names = required(tile, key + "Palette", ListTag.class);
        List<ID> palette = new ArrayList<>();
        for (Object name : names) {
            if (!(name instanceof StringTag string)) {
                throw new IOException("Invalid " + key + " palette");
            }
            palette.add(ID.of(string.getValue()));
        }
        required(tile, key, IntArrayTag.class);
        int[] cells = tile.getIntArray(key);
        int width = biomes ? slice.getBiomeWidth() : slice.getWidth();
        int height = biomes ? slice.getBiomeHeight() : slice.getHeight();
        if (cells.length != Math.multiplyExact(width, height)) {
            throw new IOException("Invalid " + key + " cell count");
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int index = cells[x * height + y];
                if (index < 0 || index > palette.size()) {
                    throw new IOException("Invalid " + key + " palette index: " + index);
                }
                if (index == 0) {
                    continue;
                }
                ID id = palette.get(index - 1);
                if (biomes) {
                    slice.setBiome(x, y, () -> id);
                } else {
                    slice.setBlock(x, y, blocks.create(id));
                }
            }
        }
    }

    private static void expect(CompoundTag tag, String key, int expected) throws IOException {
        required(tag, key, IntTag.class);
        if (tag.getInt(key) != expected) {
            throw new IOException("Unexpected capture " + key);
        }
    }

    private static <T extends Tag<?>> T required(CompoundTag tag, String key, Class<T> type) throws IOException {
        if (!type.isInstance(tag.get(key))) {
            throw new IOException("Missing or invalid capture field: " + key);
        }
        return type.cast(tag.get(key));
    }

    private static CompoundTag read(Path file) throws IOException {
        try {
            if (NBTUtil.read(file.toFile(), true).getTag() instanceof CompoundTag compound) {
                return compound;
            }
            throw new IOException("Expected NBT compound in " + file);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid inspection NBT in " + file, exception);
        }
    }

    private static void write(Path file, CompoundTag tag) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".capture-", ".tmp");
        try {
            NBTUtil.write(tag, temporary.toFile(), true);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
