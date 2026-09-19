package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.simulation.generator.Chunk;
import com.kntrel.mc.underilla.core.simulation.generator.Generator;
import com.kntrel.mc.underilla.core.simulation.generator.WorldInfo;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

public final class InspectorGenerator extends Generator {

    private static final int SLICE_Z = 0;
    private static final int PIXELS_PER_BLOCK = 2;

    private final WorldGenerationPlan plan;
    private final BufferedImage image;

    public InspectorGenerator(WorldGenerationPlan plan, TestWorld world, long seed, int chunkSize, BufferedImage image) {
        super(world, seed, chunkSize, 1);
        this.image = Objects.requireNonNull(image, "image");
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    @Override
    public boolean generateNoise(Chunk chunk) { return plan.flags().noise(); }

    @Override
    public boolean generateSurface(Chunk chunk) { return plan.flags().surface(); }

    @Override
    public boolean generateCaves(Chunk chunk) { return plan.flags().carvers(); }

    @Override
    public boolean generateFeatures(Chunk chunk) { return plan.flags().features(); }

    @Override
    public void afterNoise(WorldInfo worldInfo, Chunk chunk) {
        this.render(worldInfo, chunk);
        this.patch(this.plan.afterNoise(), chunk);
    }

    @Override
    public void afterSurface(WorldInfo worldInfo, Chunk chunk) {
        this.render(worldInfo, chunk);
        this.patch(this.plan.afterSurface(), chunk);
    }

    @Override
    public void afterCaves(WorldInfo worldInfo, Chunk chunk) {
        this.render(worldInfo, chunk);
        this.patch(this.plan.afterCarvers(), chunk);
    }

    @Override
    public void afterFeatures(WorldInfo worldInfo, Chunk chunk) {
        this.render(worldInfo, chunk);
        this.patch(this.plan.afterFeatures(), chunk);
    }

    @Override
    public void afterLoad(WorldInfo worldInfo, Chunk chunk) {
        this.render(worldInfo, chunk);
        this.patch(this.plan.afterLoad(), chunk);
    }


    private void render(WorldInfo worldInfo, Chunk chunk) {
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

    private void patch(Patcher<ChunkData> patcher, Chunk chunk) {
        ChunkData chunkData = this.world.chunkData(chunk.x(), chunk.z()).orElse(null);
        if (chunkData == null) { return; }
        patcher.patch(chunkData);
    }
}
