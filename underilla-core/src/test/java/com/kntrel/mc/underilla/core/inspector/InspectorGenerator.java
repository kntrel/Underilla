package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.simulation.biome.BiomeProvider;
import com.kntrel.mc.underilla.core.simulation.generator.Chunk;
import com.kntrel.mc.underilla.core.simulation.generator.Generator;
import com.kntrel.mc.underilla.core.simulation.generator.WorldInfo;
import java.util.Objects;
import java.util.function.Function;

/**
 * Test-only adapter that runs an already instrumented generation plan against the synthetic
 * simulator. The reusable inspection implementation lives in the main source set.
 */
final class InspectorGenerator extends Generator {

    private final WorldGenerationPlan plan;
    private final BiomeProvider biomeProvider;

    InspectorGenerator(WorldGenerationPlan plan, TestWorld world, long seed, int chunkSize) {
        super(world, seed, chunkSize, 1);
        this.plan = Objects.requireNonNull(plan, "plan");
        this.biomeProvider = new InspectorBiomeProvider(plan.biomePatch(), super.getBiomeProvider());
    }

    @Override
    public BiomeProvider getBiomeProvider() {
        return biomeProvider;
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
        apply(chunk, plan::tryAfterNoise);
    }

    @Override
    public void afterSurface(WorldInfo worldInfo, Chunk chunk) {
        apply(chunk, plan::tryAfterSurface);
    }

    @Override
    public void afterCaves(WorldInfo worldInfo, Chunk chunk) {
        apply(chunk, plan::tryAfterCarvers);
    }

    @Override
    public void afterFeatures(WorldInfo worldInfo, Chunk chunk) {
        apply(chunk, plan::tryAfterFeatures);
    }

    @Override
    public void afterLoad(WorldInfo worldInfo, Chunk chunk) {
        apply(chunk, plan::tryAfterLoad);
    }

    private void apply(Chunk chunk, Function<ChunkData, Boolean> phase) {
        world.chunkData(chunk.x(), chunk.z()).ifPresent(phase::apply);
    }
}
