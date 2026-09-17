package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.simulation.generator.Chunk;
import com.kntrel.mc.underilla.core.simulation.generator.Generator;
import com.kntrel.mc.underilla.core.simulation.generator.WorldInfo;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public final class Inspector extends Generator {

     static void main(String[] args) {
        long seed = args.length == 0 ? 0L : Long.parseLong(args[0]);
        int chunkSize = args.length < 2 ? 8 : Integer.parseInt(args[1]);
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        BufferedImage image = new BufferedImage(
                Math.multiplyExact(Math.multiplyExact(chunkSize, GenerationConstants.CHUNK_SIZE), PIXELS_PER_BLOCK),
                Math.multiplyExact(MAXIMUM_Y - MINIMUM_Y, PIXELS_PER_BLOCK),
                BufferedImage.TYPE_INT_RGB);
        Inspector inspector = new Inspector(
                new TestWorld(
                        MINIMUM_Y,
                        MAXIMUM_Y,
                        TestBlock.air("minecraft:air"),
                        new TestBiome("minecraft:plains")),
                seed,
                chunkSize,
                image);
        inspector.generate();
        try {
            WorldSliceRenderer.writePng(image, OUTPUT);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write inspector image to " + OUTPUT, exception);
        }
    }

    private static final int MINIMUM_Y = -64;
    private static final int MAXIMUM_Y = 320;
    private static final int SLICE_Z = 0;
    private static final int PIXELS_PER_BLOCK = 2;
    private static final Path OUTPUT = Path.of("build", "inspect", "after-noise.png");
    private final BufferedImage image;

    public Inspector(TestWorld world, long seed, int chunkSize, BufferedImage image) {
        super(world, seed, chunkSize, 1);
        this.image = java.util.Objects.requireNonNull(image, "image");
    }

    @Override
    public boolean generateNoise(Chunk chunk) { return true; }

    @Override
    public void afterNoise(WorldInfo worldInfo, Chunk chunk) {
        int startX = Math.multiplyExact(chunk.x(), GenerationConstants.CHUNK_SIZE);
        WorldSlice slice = WorldSlice.from(
                world,
                SLICE_Z,
                startX,
                startX + GenerationConstants.CHUNK_SIZE,
                worldInfo.minimumY(),
                worldInfo.maximumY());
        BufferedImage chunkImage = WorldSliceRenderer.render(slice, PIXELS_PER_BLOCK);
        Graphics2D graphics = image.createGraphics();
        try {
            int imageX = startX * PIXELS_PER_BLOCK;
            graphics.drawImage(chunkImage, imageX, 0, null);
        } finally {
            graphics.dispose();
        }
    }

    @Override
    public boolean generateSurface(Chunk chunk) { return true; }

    @Override
    public void afterSurface(WorldInfo worldInfo, Chunk chunk) {}

    @Override
    public boolean generateCaves(Chunk chunk) { return true; }

    @Override
    public void afterCaves(WorldInfo worldInfo, Chunk chunk) {}

    @Override
    public boolean generateFeatures(Chunk chunk) { return true; }

    @Override
    public void afterFeatures(WorldInfo worldInfo, Chunk chunk) {}

    @Override
    public void afterLoad(WorldInfo worldInfo, Chunk chunk) {}
}
