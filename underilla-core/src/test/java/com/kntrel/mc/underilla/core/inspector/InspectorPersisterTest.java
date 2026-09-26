package com.kntrel.mc.underilla.core.inspector;

import static org.junit.jupiter.api.Assertions.*;

import com.jkantrell.nbt.io.NBTUtil;
import com.jkantrell.nbt.tag.CompoundTag;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.generation.Phase;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InspectorPersisterTest {

    private static final InspectionStage STAGE = InspectionStage.before(Phase.NOISE);
    private static final BlockFactory BLOCKS = new BlockFactory() {
        public Block air() { return TestBlock.air("minecraft:air"); }
        public Block create(ID id) {
            return id.equals(ID.of("air")) ? air() : TestBlock.solid(id.toString());
        }
    };

    @TempDir Path output;

    @Test
    void reopensPartialCapturesForBothAxesWithNegativeCoordinatesAndUnalignedBiomes() throws IOException {
        for (InspectionRegion.Axis axis : InspectionRegion.Axis.values()) {
            Path folder = output.resolve(axis.name());
            InspectionRegion region = new InspectionRegion(axis, -17, -1, 2, -3, 6);
            int chunkX = axis == InspectionRegion.Axis.Z ? -1 : -2;
            int chunkZ = axis == InspectionRegion.Axis.Z ? -2 : -1;
            WorldSlice tile = new WorldSlice(-16, -3, 16, 9);
            tile.setBlock(0, 0, BLOCKS.air());
            tile.setBlock(15, 8, TestBlock.solid("custom:stone"));
            tile.setBiomeAt(0, 0, () -> ID.of("custom:caves"));
            tile.setBiomeAt(15, 8, () -> ID.of("plains"));
            store(folder, region).save(STAGE, chunkX, chunkZ, tile);

            var captures = store(folder, region).load();
            assertEquals(1, captures.size()); // The other expected chunk has not arrived yet.
            var capture = captures.getFirst();
            assertEquals(STAGE, capture.stage());
            assertEquals(chunkX, capture.chunkX());
            assertEquals(chunkZ, capture.chunkZ());
            WorldSlice restored = capture.slice();
            assertEquals(-16, restored.getOffsetX());
            assertEquals(-3, restored.getOffsetY());
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 9; y++) {
                    assertEquals(tile.getBlock(x, y) == null ? null : tile.getBlock(x, y).id(),
                            restored.getBlock(x, y) == null ? null : restored.getBlock(x, y).id());
                    assertEquals(tile.getBiomeAt(x, y) == null ? null : tile.getBiomeAt(x, y).id(),
                            restored.getBiomeAt(x, y) == null ? null : restored.getBiomeAt(x, y).id());
                }
            }
            assertThrows(IllegalStateException.class, () -> restored.setBlock(0, 0, BLOCKS.air()));
        }
    }

    @Test
    void rejectsIncompatibleManifestWithoutChangingCaptures() throws IOException {
        InspectionRegion region = InspectionRegion.zSlice(0, 0, 1, 0, 4);
        store(output, region).save(STAGE, 0, 0, new WorldSlice(16, 4));
        assertThrows(IOException.class, () -> new InspectorPersister(output, "other-world", "settings", region, Set.of(STAGE), BLOCKS));
        assertThrows(IOException.class, () -> new InspectorPersister(output, "world", "other-settings", region, Set.of(STAGE), BLOCKS));
        assertThrows(IOException.class, () -> store(output, InspectionRegion.xSlice(0, 0, 1, 0, 4)));
        assertThrows(IOException.class, () -> new InspectorPersister(output, "world", "settings", region,
                Set.of(InspectionStage.after(Phase.NOISE)), BLOCKS));
        assertEquals(1, store(output, region).load().size());
    }

    @Test
    void ignoresTemporaryFilesAndReplacesDuplicateCapture() throws IOException {
        InspectionRegion region = InspectionRegion.zSlice(0, 0, 1, 0, 4);
        InspectorPersister store = store(output, region);
        Files.writeString(output.resolve("captures/.capture-interrupted.tmp"), "unfinished");
        assertTrue(store.load().isEmpty());
        WorldSlice tile = new WorldSlice(16, 4);
        store.save(STAGE, 0, 0, tile);
        tile.setBlock(0, 0, TestBlock.solid("stone"));
        store.save(STAGE, 0, 0, tile);
        var restored = store(output, region).load();
        assertEquals(1, restored.size());
        assertEquals(ID.of("stone"), restored.getFirst().slice().getBlock(0, 0).id());
    }

    @Test
    void rejectsCorruptCommittedCaptureAndMissingManifest() throws IOException {
        InspectionRegion region = InspectionRegion.zSlice(0, 0, 1, 0, 4);
        InspectorPersister store = store(output, region);
        store.save(STAGE, 0, 0, new WorldSlice(16, 4));
        Path file = output.resolve("captures/noise/before_patch/0_0.nbt");
        CompoundTag tag = (CompoundTag) NBTUtil.read(file.toFile(), true).getTag();
        tag.getIntArray("blocks")[0] = 1; // No entries exist in this tile's palette.
        NBTUtil.write(tag, file.toFile(), true);
        IOException error = assertThrows(IOException.class, store::load);
        assertTrue(error.getMessage().contains(file.toString()));
        Files.delete(output.resolve("captures/manifest.nbt"));
        assertThrows(IOException.class, () -> store(output, region));
    }

    @Test
    void rejectsWrongPositionStageAndTileDimensions() throws IOException {
        InspectorPersister store = store(output, InspectionRegion.zSlice(0, 0, 1, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> store.save(STAGE, 1, 0, new WorldSlice(16, 4)));
        assertThrows(IllegalArgumentException.class,
                () -> store.save(InspectionStage.after(Phase.NOISE), 0, 0, new WorldSlice(16, 4)));
        assertThrows(IllegalArgumentException.class, () -> store.save(STAGE, 0, 0, new WorldSlice(16, 5)));
        assertTrue(store.load().isEmpty());
    }

    private static InspectorPersister store(Path output, InspectionRegion region) throws IOException {
        return new InspectorPersister(output, "world", "settings", region, Set.of(STAGE), BLOCKS);
    }
}
