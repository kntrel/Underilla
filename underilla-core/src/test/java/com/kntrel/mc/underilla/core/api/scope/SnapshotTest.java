package com.kntrel.mc.underilla.core.api.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.World;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.patch.ChunkBlock;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SnapshotTest {

    @Test
    void biomeCellsUseFloorDivisionForNegativeBlockCoordinatesAndSurviveRecapture() {
        Biome biome = new TestBiome("minecraft:plains");
        BiomeSnapshot snapshot = BiomeSnapshot.atBlock(biome, -1, -5, -17);

        assertEquals(-1, snapshot.cellX());
        assertEquals(-2, snapshot.cellY());
        assertEquals(-5, snapshot.cellZ());
        assertSame(snapshot, BiomeSnapshot.of(snapshot));
    }

    @Test
    void structureDimensionsDescribeBoundingExtentsIndependentlyOfPlacementOrigin() {
        StructureSnapshot.Bounds bounds = new StructureSnapshot.Bounds(-16.5, 40, -2.25, 3.5, 56, 8.75);
        StructureSnapshot structure = StructureSnapshot.of(ID.of("minecraft:mineshaft"), 0, 48, 0, bounds);

        assertEquals(new StructureSnapshot.Origin(0, 48, 0), structure.origin());
        assertEquals(20, structure.bounds().width());
        assertEquals(16, structure.bounds().height());
        assertEquals(11, structure.bounds().depth());
    }

    @Test
    void structureBoundsRejectReversedAndNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new StructureSnapshot.Bounds(5, 0, 0, 4, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureSnapshot.Bounds(0, 5, 0, 1, 4, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureSnapshot.Bounds(0, 0, 5, 1, 1, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureSnapshot.Bounds(Double.NaN, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureSnapshot.Bounds(0, 0, 0, 1, Double.POSITIVE_INFINITY, 1));
    }

    @Test
    void capturesWorldAndBiomeValuesWithoutRetainingLiveReaders() {
        AtomicReference<String> name = new AtomicReference<>("reference");
        AtomicLong seed = new AtomicLong(872364827346L);
        AtomicReference<ID> biomeId = new AtomicReference<>(ID.of("minecraft:plains"));
        World source = new World() {
            @Override public String name() { return name.get(); }
            @Override public ID dimension() { return ID.of("minecraft:overworld"); }
            @Override public long seed() { return seed.get(); }
            @Override public int minHeight() { return -64; }
            @Override public int maxHeight() { return 320; }
        };
        Biome biome = biomeId::get;
        WorldSnapshot worldSnapshot = WorldSnapshot.of(source);
        BiomeSnapshot biomeSnapshot = BiomeSnapshot.of(biome);

        name.set("changed");
        seed.set(-42L);
        biomeId.set(ID.of("minecraft:desert"));

        assertEquals("reference", worldSnapshot.name());
        assertEquals(872364827346L, worldSnapshot.seed());
        assertEquals(-64, worldSnapshot.minHeight());
        assertEquals(320, worldSnapshot.maxHeight());
        assertEquals(ID.of("minecraft:overworld"), worldSnapshot.dimension());
        assertEquals(ID.of("minecraft:plains"), biomeSnapshot.id());
    }

    @Test
    void capturesBlockPropertiesAndGlobalCoordinatesBeforeTheCandidateChanges() {
        TestBlock stone = TestBlock.solid("minecraft:stone");
        TestBlock air = TestBlock.air("minecraft:air");
        TestBiome plains = new TestBiome("minecraft:plains");
        TestChunkGrid target = new TestChunkGrid(-2, 3, 0, 16, stone, plains);
        ChunkBlock candidate = new ChunkBlock(target, 1, 7, 2, stone);
        BlockSnapshot snapshot = BlockSnapshot.of(candidate.block());
        BlockSnapshot destination = BlockSnapshot.of(candidate.destinationBlock());
        ChunkSnapshot chunk = ChunkSnapshot.of(target);

        candidate.replace(air);
        target.setBlock(1, 7, 2, air);

        assertEquals(ID.of("minecraft:stone"), snapshot.id());
        assertEquals(-31, snapshot.x());
        assertEquals(7, snapshot.y());
        assertEquals(50, snapshot.z());
        assertTrue(snapshot.isSolid());
        assertFalse(snapshot.isAir());
        assertFalse(snapshot.isLiquid());
        assertFalse(snapshot.isWaterloggable());
        assertTrue(snapshot.isLegal());
        assertEquals(snapshot, destination);
        assertEquals(-2, chunk.x());
        assertEquals(3, chunk.z());
    }

    @Test
    void preservesUnavailableValuesDuringSourceOnlyDecisions() {
        ChunkSnapshot chunk = ChunkSnapshot.of(-1, -2);
        DualBlockScope snapshot = DualBlockScope.of(chunk, new TestBiome("minecraft:plains"),
                TestBlock.air("minecraft:air"), null, null);

        assertNull(snapshot.source().world());
        assertNull(snapshot.target().world());
        assertNull(snapshot.target().biome());
        assertNull(snapshot.target().block());
        assertTrue(snapshot.source().block().isAir());
        assertEquals(chunk, snapshot.source().chunk());
    }
}
