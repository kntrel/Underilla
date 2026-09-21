package com.kntrel.mc.underilla.core.reader;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.jkantrell.nbt.io.NBTSerializer;
import com.jkantrell.nbt.io.NamedTag;
import com.jkantrell.nbt.tag.CompoundTag;
import com.jkantrell.nbt.tag.DoubleTag;
import com.jkantrell.nbt.tag.IntTag;
import com.jkantrell.nbt.tag.ListTag;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestDiskWorldReader;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskWorldReaderTest {

    @TempDir
    Path temporaryDirectory;

    private Path arbitraryTerrainDirectory;
    private Path regionDirectory;
    private Path entityDirectory;
    private Path missingRegionDirectory;
    private Path missingEntityDirectory;

    @BeforeEach
    void setUpTemporaryPaths() throws IOException {
        arbitraryTerrainDirectory = Files.createDirectories(temporaryDirectory.resolve("arbitrary/source/terrain"));
        regionDirectory = Files.createDirectories(temporaryDirectory.resolve("world/region"));
        entityDirectory = Files.createDirectories(temporaryDirectory.resolve("world/entities"));
        missingRegionDirectory = temporaryDirectory.resolve("missing-world/region");
        missingEntityDirectory = temporaryDirectory.resolve("missing-world/entities");
    }

    @Test
    void readsTheProvidedRegionDirectoryWithoutWorldOrDimensionAssumptions() throws Exception {
        Files.copy(resourcePath("mca/surface.mca"), arbitraryTerrainDirectory.resolve("r.0.0.mca"), REPLACE_EXISTING);

        TestDiskWorldReader reader = new TestDiskWorldReader(
                arbitraryTerrainDirectory.toFile(), 1,
                new TestBlockFactory(TestBlock.air("minecraft:air")));

        assertEquals("minecraft:stone", reader.blockAt(0, 0, 0).orElseThrow().id().toString());
    }

    @Test
    void sharedCacheReusesReadersAndEvictsThemAlongWithOtherTopics() throws Exception {
        writeEntityRegion(regionDirectory.resolve("r.0.0.mca"));
        TestDiskWorldReader source = new TestDiskWorldReader(regionDirectory.toFile(), 1,
                new TestBlockFactory(TestBlock.air("minecraft:air")));
        ChunkCache cache = new ChunkCache(2);
        DiskWorldReader reader = source.withChunkCache(cache);
        ChunkReader first = reader.readChunk(0, 0).orElseThrow();
        assertSame(first, reader.readChunk(0, 0).orElseThrow());
        assertSame(first, source.withChunkCache(cache).readChunk(0, 0).orElseThrow());

        cache.put(0, 0, "other topic");
        cache.put(1, 0, "second chunk");
        reader.readChunk(0, 0);
        cache.put(2, 0, "third chunk");
        assertSame(first, reader.readChunk(0, 0).orElseThrow());
        assertEquals("other topic", cache.get(0, 0, String.class).orElseThrow());

        // The region remains in memory, even when the generation cache evicts its reader.
        Files.delete(regionDirectory.resolve("r.0.0.mca"));
        cache.put(3, 0, "fourth chunk");
        cache.put(4, 0, "fifth chunk");
        assertNotSame(first, reader.readChunk(0, 0).orElseThrow());
    }

    @Test
    void separatesSourceWorldsAndGenerationPlanCaches() throws Exception {
        writeEntityRegion(regionDirectory.resolve("r.0.0.mca"));
        writeEntityRegion(arbitraryTerrainDirectory.resolve("r.0.0.mca"));
        TestBlockFactory blocks = new TestBlockFactory(TestBlock.air("minecraft:air"));
        TestDiskWorldReader reference = new TestDiskWorldReader(regionDirectory.toFile(), 1, blocks);
        TestDiskWorldReader caves = new TestDiskWorldReader(arbitraryTerrainDirectory.toFile(), 1, blocks);
        ChunkCache firstPlan = new ChunkCache(1);
        DiskWorldReader referenceView = reference.withChunkCache(firstPlan);
        DiskWorldReader cavesView = caves.withChunkCache(firstPlan);
        ChunkReader referenceChunk = referenceView.readChunk(0, 0).orElseThrow();
        ChunkReader cavesChunk = cavesView.readChunk(0, 0).orElseThrow();
        assertNotSame(referenceChunk, cavesChunk);
        assertSame(referenceChunk, referenceView.readChunk(0, 0).orElseThrow());
        assertSame(cavesChunk, cavesView.readChunk(0, 0).orElseThrow());

        DiskWorldReader secondView = reference.withChunkCache(new ChunkCache(1));
        assertNotSame(referenceChunk, secondView.readChunk(0, 0).orElseThrow());
        assertSame(referenceChunk, referenceView.readChunk(0, 0).orElseThrow());
    }

    @Test
    void rejectsAMissingRegionDirectory() {
        assertThrows(NoSuchFieldException.class, () -> new TestDiskWorldReader(
                missingRegionDirectory.toFile(), 1,
                new TestBlockFactory(TestBlock.air("minecraft:air"))));
    }

    @Test
    void injectsEntitiesFromTheCorrespondingEntityRegion() throws Exception {
        Files.copy(resourcePath("mca/surface.mca"), regionDirectory.resolve("r.0.0.mca"), REPLACE_EXISTING);
        writeEntityRegion(entityDirectory.resolve("r.0.0.mca"));

        TestDiskWorldReader reader = new TestDiskWorldReader(
                regionDirectory.toFile(), entityDirectory.toFile(), 1,
                new TestBlockFactory(TestBlock.air("minecraft:air")));

        EntityView entity = reader.readChunk(0, 0).orElseThrow().getEntities().getFirst();
        assertEquals("minecraft:armor_stand", entity.tag().getString("id"));
        assertEquals(3955, entity.dataVersion());
        assertEquals(1.5, entity.tag().getListTag("Pos").asDoubleTagList().get(0).asDouble());
    }

    @Test
    void treatsAMissingEntityRegionAsAnEmptyEntityList() throws Exception {
        Files.copy(resourcePath("mca/surface.mca"), regionDirectory.resolve("r.0.0.mca"), REPLACE_EXISTING);

        TestDiskWorldReader reader = new TestDiskWorldReader(
                regionDirectory.toFile(), entityDirectory.toFile(), 1,
                new TestBlockFactory(TestBlock.air("minecraft:air")));

        ChunkReader chunk = reader.readChunk(0, 0).orElseThrow();
        assertEquals("minecraft:stone", chunk.blockAt(0, 0, 0).orElseThrow().id().toString());
        assertEquals(0, chunk.getEntities().size());
    }

    @Test
    void treatsAMissingTerrainRegionAsOutsideTheSurface() throws Exception {
        TestDiskWorldReader reader = new TestDiskWorldReader(
                regionDirectory.toFile(), entityDirectory.toFile(), 1,
                new TestBlockFactory(TestBlock.air("minecraft:air")));

        assertEquals(0, reader.readChunk(0, 0).stream().count());
    }

    @Test
    void rejectsAMissingEntityRegionDirectoryWhenOneIsProvided() throws Exception {
        assertThrows(NoSuchFieldException.class, () -> new TestDiskWorldReader(
                regionDirectory.toFile(), missingEntityDirectory.toFile(), 1,
                new TestBlockFactory(TestBlock.air("minecraft:air"))));
    }

    private static void writeEntityRegion(Path path) throws Exception {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:armor_stand");
        ListTag<DoubleTag> position = new ListTag<>(DoubleTag.class);
        position.addDouble(1.5);
        position.addDouble(64);
        position.addDouble(2.5);
        entity.put("Pos", position);

        ListTag<CompoundTag> entities = new ListTag<>(CompoundTag.class);
        entities.add(entity);

        CompoundTag entityChunk = new CompoundTag();
        entityChunk.putInt("DataVersion", 3955);
        ListTag<IntTag> chunkPosition = new ListTag<>(IntTag.class);
        chunkPosition.addInt(0);
        chunkPosition.addInt(0);
        entityChunk.put("Position", chunkPosition);
        entityChunk.put("Entities", entities);

        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(compressedBytes)) {
            new NBTSerializer(false).toStream(new NamedTag(null, entityChunk), compressed);
        }

        byte[] payload = compressedBytes.toByteArray();
        int sectorCount = Math.ceilDiv(payload.length + 5, 4096);
        try (RandomAccessFile region = new RandomAccessFile(path.toFile(), "rw")) {
            region.setLength((2L + sectorCount) * 4096);
            region.writeInt((2 << 8) | sectorCount);
            region.seek(8192);
            region.writeInt(payload.length + 1);
            region.writeByte(2);
            region.write(payload);
        }
    }

    private static Path resourcePath(String path) throws URISyntaxException {
        URL resource = DiskWorldReaderTest.class.getClassLoader().getResource(path);
        assertNotNull(resource, "Missing test resource " + path);
        return Path.of(resource.toURI());
    }
}
