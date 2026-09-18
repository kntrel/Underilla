package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.patch.ChunkProfiledPatcher;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.patch.PatcherPipeline;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.profiling.Tracker;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Builder for a complete generation plan, with safe no-op patch defaults.
 */
public final class WorldGenerationPlanBuilder {

    private static final Instrumenter NO_OP_INSTRUMENTER = new Instrumenter(_ -> {});
    private static final Patcher<ChunkData> NO_OP_CHUNK_PATCHER = _ -> {};
    private static final Patcher<BiomeData> NO_OP_BIOME_PATCHER = _ -> {};
    private static final Altimeter NO_OP_ALTIMETER = (worldInfo, x, z, heightMap) -> 0;
    private static final ChunkCoverage ALL_CHUNKS = (chunkX, chunkZ) -> true;

    private ChunkCoverage coverage = ALL_CHUNKS;
    private List<Patcher<ChunkData>> afterNoise = List.of();
    private List<Patcher<ChunkData>> afterSurface = List.of();
    private List<Patcher<ChunkData>> afterCarvers = List.of();
    private List<Patcher<ChunkData>> afterFeatures = List.of();
    private List<Patcher<ChunkData>> afterLoad = List.of();
    private Patcher<BiomeData> biomePatch = NO_OP_BIOME_PATCHER;
    private Instrumenter instrumenter = NO_OP_INSTRUMENTER;
    private boolean noise = true;
    private boolean surface = true;
    private boolean carvers = true;
    private boolean features = true;
    private boolean mobs = true;
    private boolean structures = true;
    private Altimeter altimeter = NO_OP_ALTIMETER;

    /** Sets the chunks for which this plan should replace the platform fallback. */
    public WorldGenerationPlanBuilder coverage(ChunkCoverage coverage) {
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        return this;
    }

    /**
     * Sets the ordered patch pipeline that runs after vanilla noise generation.
     */
    @SafeVarargs
    public final WorldGenerationPlanBuilder afterNoise(Patcher<ChunkData>... patchers) {
        afterNoise = listPatcher("afterNoise", patchers);
        return this;
    }

    /**
     * Sets the ordered patch pipeline that runs after vanilla surface rules.
     */
    @SafeVarargs
    public final WorldGenerationPlanBuilder afterSurface(Patcher<ChunkData>... patchers) {
        afterSurface = listPatcher("afterSurface", patchers);
        return this;
    }

    public WorldGenerationPlanBuilder afterSurface(List<? extends Patcher<ChunkData>> patchers) {
        afterSurface = listPatcher("afterSurface", patchers);
        return this;
    }

    /**
     * Sets the ordered patch pipeline that runs after vanilla carvers.
     */
    @SafeVarargs
    public final WorldGenerationPlanBuilder afterCarvers(Patcher<ChunkData>... patchers) {
        afterCarvers = listPatcher("afterCarvers", patchers);
        return this;
    }

    public WorldGenerationPlanBuilder afterCarvers(List<? extends Patcher<ChunkData>> patchers) {
        afterCarvers = listPatcher("afterCarvers", patchers);
        return this;
    }

    /**
     * Sets the ordered patch pipeline that runs after vanilla features and structure pieces.
     */
    @SafeVarargs
    public final WorldGenerationPlanBuilder afterFeatures(Patcher<ChunkData>... patchers) {
        afterFeatures = listPatcher("afterFeatures", patchers);
        return this;
    }

    public WorldGenerationPlanBuilder afterFeatures(List<? extends Patcher<ChunkData>> patchers) {
        afterFeatures = listPatcher("afterFeatures", patchers);
        return this;
    }

    /**
     * Sets the ordered patch pipeline that runs after the generated chunk becomes live in its world.
     */
    @SafeVarargs
    public final WorldGenerationPlanBuilder afterLoad(Patcher<ChunkData>... patchers) {
        afterLoad = listPatcher("afterLoad", patchers);
        return this;
    }

    public WorldGenerationPlanBuilder afterLoad(List<? extends Patcher<ChunkData>> patchers) {
        afterLoad = listPatcher("afterLoad", patchers);
        return this;
    }

    public WorldGenerationPlanBuilder biomePatch(Patcher<BiomeData> biomePatch) {
        this.biomePatch = Objects.requireNonNull(biomePatch, "biomePatch");
        return this;
    }

    /** Sets the recorder used to profile supplied patchers and the altimeter. */
    public WorldGenerationPlanBuilder instrumenter(Instrumenter instrumenter) {
        this.instrumenter = Objects.requireNonNull(instrumenter, "instrumenter");
        return this;
    }

    public WorldGenerationPlanBuilder flags(GenerationFlags flags) {
        GenerationFlags configuredFlags = Objects.requireNonNull(flags, "flags");
        noise = configuredFlags.noise();
        surface = configuredFlags.surface();
        carvers = configuredFlags.carvers();
        features = configuredFlags.features();
        mobs = configuredFlags.mobs();
        structures = configuredFlags.structures();
        return this;
    }

    public WorldGenerationPlanBuilder noise(boolean enabled) {
        noise = enabled;
        return this;
    }

    public WorldGenerationPlanBuilder surface(boolean enabled) {
        surface = enabled;
        return this;
    }

    public WorldGenerationPlanBuilder carvers(boolean enabled) {
        carvers = enabled;
        return this;
    }

    public WorldGenerationPlanBuilder features(boolean enabled) {
        features = enabled;
        return this;
    }

    public WorldGenerationPlanBuilder mobs(boolean enabled) {
        mobs = enabled;
        return this;
    }

    public WorldGenerationPlanBuilder structures(boolean enabled) {
        structures = enabled;
        return this;
    }

    public WorldGenerationPlanBuilder altimeter(Altimeter altimeter) {
        this.altimeter = Objects.requireNonNull(altimeter, "altimeter");
        return this;
    }

    public WorldGenerationPlan done() {
        GenerationFlags flags = new GenerationFlags(
                noise,
                surface,
                carvers,
                features,
                mobs,
                structures
        );
        return new WorldGenerationPlan(
                coverage,
                profiledPipeline(afterNoise),
                profiledPipeline(afterSurface),
                profiledPipeline(afterCarvers),
                profiledPipeline(afterFeatures),
                profiledPipeline(afterLoad),
                profiledBiomePatcher(),
                flags,
                profiledAltimeter()
        );
    }

    @SafeVarargs
    private static List<Patcher<ChunkData>> listPatcher(String phase, Patcher<ChunkData>... patchers) {
        Objects.requireNonNull(patchers, phase + " patchers");
        Arrays.stream(patchers).forEach(patcher -> Objects.requireNonNull(patcher, phase + " patcher"));
        return List.of(patchers);
    }

    private static List<Patcher<ChunkData>> listPatcher(
            String phase,
            List<? extends Patcher<ChunkData>> patchers
    ) {
        Objects.requireNonNull(patchers, phase + " patchers");
        patchers.forEach(patcher -> Objects.requireNonNull(patcher, phase + " patcher"));
        return List.copyOf(patchers);
    }

    private Patcher<ChunkData> profiledPipeline(List<Patcher<ChunkData>> patchers) {
        if (patchers.isEmpty()) {
            return NO_OP_CHUNK_PATCHER;
        }
        if (instrumenter == NO_OP_INSTRUMENTER) {
            return new PatcherPipeline<>(patchers);
        }
        List<Patcher<ChunkData>> profiledPatchers = patchers.stream()
                .map(p -> (Patcher<ChunkData>) new ChunkProfiledPatcher(p, instrumenter))
                .toList();
        return new PatcherPipeline<>(profiledPatchers);
    }

    private Patcher<BiomeData> profiledBiomePatcher() {
        if (instrumenter == NO_OP_INSTRUMENTER || biomePatch == NO_OP_BIOME_PATCHER) {
            return biomePatch;
        }
        Patcher<BiomeData> patcher = biomePatch;
        Tracker tracker = instrumenter.tracker(patcher.getClass());
        return biome -> {
            try (var _ = tracker.stopwatch("patch")) {
                patcher.patch(biome);
            }
        };
    }

    private Altimeter profiledAltimeter() {
        if (instrumenter == NO_OP_INSTRUMENTER || altimeter == NO_OP_ALTIMETER) {
            return altimeter;
        }
        return new ProfiledAltimeter(altimeter, instrumenter);
    }

}
