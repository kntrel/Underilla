package com.kntrel.mc.underilla.core.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kntrel.mc.underilla.core.api.HeightMapType;
import com.kntrel.mc.underilla.core.api.WorldInfo;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestChunkGrid;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import org.junit.jupiter.api.Test;

class SurfaceAltimeterTest {

    private static final TestBlock AIR = TestBlock.air("minecraft:air");
    private static final TestBlock STONE = TestBlock.solid("minecraft:stone");
    private static final TestBlock LEAVES = TestBlock.solid("minecraft:oak_leaves");
    private static final TestBlock WATER = TestBlock.liquid("minecraft:water");
    private static final TestBiome PLAINS = new TestBiome("minecraft:plains");
    private static final WorldInfo WORLD_INFO = new TestWorldInfo(-4, 8);

    @Test
    void missingReferenceChunkUsesThePlatformFallbackHeight() {
        SurfaceAltimeter altimeter = new SurfaceAltimeter(new TestWorld(), () -> AIR);

        assertEquals(0, altimeter.heightAt(WORLD_INFO, 0, 0, HeightMapType.WORLD_SURFACE));
    }

    @Test
    void heightMapTypeSelectsWhichReferenceBlocksCountAsSurface() {
        TestChunkGrid chunk = new TestChunkGrid(0, 0, -4, 8, AIR, PLAINS);
        chunk.setBlock(0, 1, 0, STONE);
        chunk.setBlock(0, 2, 0, LEAVES);
        chunk.setBlock(0, 3, 0, WATER);
        SurfaceAltimeter altimeter = new SurfaceAltimeter(new TestWorld().addChunk(chunk), () -> AIR);

        assertEquals(4, altimeter.heightAt(WORLD_INFO, 0, 0, HeightMapType.WORLD_SURFACE));
        assertEquals(4, altimeter.heightAt(WORLD_INFO, 0, 0, HeightMapType.WORLD_SURFACE_WG));
        assertEquals(3, altimeter.heightAt(WORLD_INFO, 0, 0, HeightMapType.OCEAN_FLOOR));
        assertEquals(3, altimeter.heightAt(WORLD_INFO, 0, 0, HeightMapType.OCEAN_FLOOR_WG));
        assertEquals(3, altimeter.heightAt(WORLD_INFO, 0, 0, HeightMapType.MOTION_BLOCKING));
        assertEquals(2, altimeter.heightAt(WORLD_INFO, 0, 0, HeightMapType.MOTION_BLOCKING_NO_LEAVES));
    }

    @Test
    void negativeCoordinatesUseFloorBasedChunkAndLocalCoordinates() {
        TestChunkGrid chunk = new TestChunkGrid(-1, -1, -4, 8, AIR, PLAINS);
        chunk.setBlock(15, 2, 15, STONE);
        SurfaceAltimeter altimeter = new SurfaceAltimeter(new TestWorld().addChunk(chunk), () -> AIR);

        assertEquals(3, altimeter.heightAt(WORLD_INFO, -1, -1, HeightMapType.WORLD_SURFACE));
    }

    @Test
    void scanStopsAtTheTargetWorldMinimumHeight() {
        TestChunkGrid chunk = new TestChunkGrid(0, 0, -4, 8, AIR, PLAINS);
        SurfaceAltimeter altimeter = new SurfaceAltimeter(new TestWorld().addChunk(chunk), () -> AIR);

        assertEquals(-4, altimeter.heightAt(WORLD_INFO, 0, 0, HeightMapType.WORLD_SURFACE));
    }

    private record TestWorldInfo(int minimumY, int maximumY) implements WorldInfo {
        @Override
        public long getSeed() { return 0; }

        @Override
        public int getMaxHeight() { return maximumY; }

        @Override
        public int getMinHeight() { return minimumY; }
    }
}
