package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.patch.BiomePatcher;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.patch.ChunkPatcherPipeline;
import com.kntrel.mc.underilla.core.patch.ProfiledPatcher;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Builder for a complete generation plan, with safe no-op patch defaults.
 */
public final class WorldGenerationPlanBuilder {

    private static final Instrumenter NO_OP_INSTRUMENTER = new Instrumenter(_ -> {});
    private static final ChunkPatcher NO_OP_CHUNK_PATCHER = _ -> {};
    private static final BiomePatcher NO_OP_BIOME_PATCHER = _ -> false;

    private List<ChunkPatcher> afterNoise = List.of();
    private List<ChunkPatcher> afterSurface = List.of();
    private List<ChunkPatcher> afterCarvers = List.of();
    private List<ChunkPatcher> afterFeatures = List.of();
    private List<ChunkPatcher> afterLoad = List.of();
    private BiomePatcher biomePatch = NO_OP_BIOME_PATCHER;
    private Instrumenter instrumenter = NO_OP_INSTRUMENTER;
    private boolean noise = true;
    private boolean surface = true;
    private boolean carvers = true;
    private boolean features = true;
    private boolean mobs = true;
    private boolean structures = true;
    private Altimeter altimeter = (worldInfo, x, z, heightMap) -> 0;

    /**
     * Sets the ordered patch pipeline that runs after vanilla noise generation.
     */
    public WorldGenerationPlanBuilder afterNoise(ChunkPatcher... patchers) {
        afterNoise = listPatcher("afterNoise", patchers);
        return this;
    }

    /**
     * Sets the ordered patch pipeline that runs after vanilla surface rules.
     */
    public WorldGenerationPlanBuilder afterSurface(ChunkPatcher... patchers) {
        afterSurface = listPatcher("afterSurface", patchers);
        return this;
    }

    /**
     * Sets the ordered patch pipeline that runs after vanilla carvers.
     */
    public WorldGenerationPlanBuilder afterCarvers(ChunkPatcher... patchers) {
        afterCarvers = listPatcher("afterCarvers", patchers);
        return this;
    }

    /**
     * Sets the ordered patch pipeline that runs after vanilla features and structure pieces.
     */
    public WorldGenerationPlanBuilder afterFeatures(ChunkPatcher... patchers) {
        afterFeatures = listPatcher("afterFeatures", patchers);
        return this;
    }

    /**
     * Sets the ordered patch pipeline that runs after the generated chunk becomes live in its world.
     */
    public WorldGenerationPlanBuilder afterLoad(ChunkPatcher... patchers) {
        afterLoad = listPatcher("afterLoad", patchers);
        return this;
    }

    public WorldGenerationPlanBuilder biomePatch(BiomePatcher biomePatch) {
        this.biomePatch = Objects.requireNonNull(biomePatch, "biomePatch");
        return this;
    }

    /** Sets the recorder used to profile each supplied chunk patcher. */
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
                profiledPipeline(afterNoise),
                profiledPipeline(afterSurface),
                profiledPipeline(afterCarvers),
                profiledPipeline(afterFeatures),
                profiledPipeline(afterLoad),
                biomePatch,
                flags,
                altimeter
        );
    }

    private static List<ChunkPatcher> listPatcher(String phase, ChunkPatcher... patchers) {
        Objects.requireNonNull(patchers, phase + " patchers");
        Arrays.stream(patchers).forEach(patcher -> Objects.requireNonNull(patcher, phase + " patcher"));
        return List.of(patchers);
    }

    private ChunkPatcher profiledPipeline(List<ChunkPatcher> patchers) {
        if (patchers.isEmpty()) {
            return NO_OP_CHUNK_PATCHER;
        }
        if (instrumenter == NO_OP_INSTRUMENTER) {
            return new ChunkPatcherPipeline(patchers);
        }
        List<ChunkPatcher> profiledPatchers = patchers.stream()
                .map(p -> (ChunkPatcher) new ProfiledPatcher(p, instrumenter))
                .toList();
        return new ChunkPatcherPipeline(profiledPatchers);
    }

}
