package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.simulation.generator.Chunk;
import com.kntrel.mc.underilla.core.simulation.generator.Generator;
import com.kntrel.mc.underilla.core.simulation.generator.WorldInfo;
import com.kntrel.mc.underilla.core.simulation.biome.BiomeProvider;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class InspectorGenerator extends Generator {

    public enum GenerationTiming {
        BEFORE,
        AFTER
    }

    public enum GenerationStage {
        NOISE(0),
        SURFACE(1),
        CAVES(2),
        FEATURES(3),
        LOAD(4);

        private final int index;

        GenerationStage(int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }
    }

    private final WorldGenerationPlan plan;
    private final BiomeProvider biomeProvider;
    private final int sliceZ;
    private final EnumMap<GenerationTiming, EnumMap<GenerationStage, List<Consumer<WorldSlice>>>> hooks;

    public InspectorGenerator(WorldGenerationPlan plan, TestWorld world, long seed, int chunkSize) {
        this(plan, world, seed, chunkSize, 0);
    }

    public InspectorGenerator(WorldGenerationPlan plan, TestWorld world, long seed, int chunkSize, int sliceZ) {
        super(world, seed, chunkSize, 1);
        this.plan = Objects.requireNonNull(plan, "plan");
        this.biomeProvider = new InspectorBiomeProvider(plan.biomePatch(), super.getBiomeProvider());
        if (sliceZ < 0 || sliceZ >= GenerationConstants.CHUNK_SIZE) {
            throw new IllegalArgumentException("sliceZ must be between 0 and "
                    + (GenerationConstants.CHUNK_SIZE - 1));
        }
        this.sliceZ = sliceZ;
        this.hooks = new EnumMap<>(GenerationTiming.class);
        for (GenerationTiming timing : GenerationTiming.values()) {
            EnumMap<GenerationStage, List<Consumer<WorldSlice>>> stageHooks = new EnumMap<>(GenerationStage.class);
            for (GenerationStage stage : GenerationStage.values()) {
                stageHooks.put(stage, new ArrayList<>());
            }
            hooks.put(timing, stageHooks);
        }
    }

    /**
     * Returns the registration point for callbacks that observe generated world slices.
     *
     * <p>For example, {@code generator.hook().after().surface(destination)} sends each
     * completed surface-stage slice to {@code destination}.</p>
     */
    public TimingHook hook() {
        return new TimingHook(this);
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
        this.runStage(GenerationStage.NOISE, worldInfo, chunk, this.plan.afterNoise());
    }

    @Override
    public void afterSurface(WorldInfo worldInfo, Chunk chunk) {
        this.runStage(GenerationStage.SURFACE, worldInfo, chunk, this.plan.afterSurface());
    }

    @Override
    public void afterCaves(WorldInfo worldInfo, Chunk chunk) {
        this.runStage(GenerationStage.CAVES, worldInfo, chunk, this.plan.afterCarvers());
    }

    @Override
    public void afterFeatures(WorldInfo worldInfo, Chunk chunk) {
        this.runStage(GenerationStage.FEATURES, worldInfo, chunk, this.plan.afterFeatures());
    }

    @Override
    public void afterLoad(WorldInfo worldInfo, Chunk chunk) {
        this.runStage(GenerationStage.LOAD, worldInfo, chunk, this.plan.afterLoad());
    }

    private void runStage(GenerationStage stage, WorldInfo worldInfo, Chunk chunk, Patcher<ChunkData> patcher) {
        this.publish(GenerationTiming.BEFORE, stage, worldInfo, chunk);
        this.patch(patcher, chunk);
        this.publish(GenerationTiming.AFTER, stage, worldInfo, chunk);
    }

    private void publish(GenerationTiming timing, GenerationStage stage, WorldInfo worldInfo, Chunk chunk) {
        List<Consumer<WorldSlice>> consumers = hooks.get(timing).get(stage);
        if (consumers.isEmpty()) {
            return;
        }
        int startX = Math.multiplyExact(chunk.x(), GenerationConstants.CHUNK_SIZE);
        WorldSlice slice = WorldSlice.from(
                world,
                sliceZ,
                startX,
                startX + GenerationConstants.CHUNK_SIZE,
                worldInfo.minimumY(),
                worldInfo.maximumY());
        for (Consumer<WorldSlice> consumer : List.copyOf(consumers)) {
            consumer.accept(slice);
        }
    }

    private void patch(Patcher<ChunkData> patcher, Chunk chunk) {
        ChunkData chunkData = this.world.chunkData(chunk.x(), chunk.z()).orElse(null);
        if (chunkData == null) { return; }
        patcher.patch(chunkData);
    }

    /** Fluent selector for the side of a generation stage to observe. */
    public static final class TimingHook {

        private final InspectorGenerator upstream;

        private TimingHook(InspectorGenerator upstream) {
            this.upstream = upstream;
        }

        public StageHook before() {
            return new StageHook(this.upstream, GenerationTiming.BEFORE);
        }

        public StageHook after() {
            return new StageHook(this.upstream, GenerationTiming.AFTER);
        }
    }

    /** Fluent selector for a generation stage. */
    public static final class StageHook {

        private final InspectorGenerator upstream;
        private final GenerationTiming timing;

        private StageHook(InspectorGenerator upstream, GenerationTiming timing) {
            this.upstream = upstream;
            this.timing = timing;
        }

        public void noise(Consumer<WorldSlice> consumer) {
            this.register(GenerationStage.NOISE, consumer);
        }

        public void surface(Consumer<WorldSlice> consumer) {
            this.register(GenerationStage.SURFACE, consumer);
        }

        public void caves(Consumer<WorldSlice> consumer) {
            this.register(GenerationStage.CAVES, consumer);
        }

        public void features(Consumer<WorldSlice> consumer) {
            this.register(GenerationStage.FEATURES, consumer);
        }

        public void load(Consumer<WorldSlice> consumer) {
            this.register(GenerationStage.LOAD, consumer);
        }

        private void register(GenerationStage stage, Consumer<WorldSlice> consumer) {
            this.upstream.hooks.get(timing).get(stage).add(Objects.requireNonNull(consumer, "consumer"));
        }
    }
}
