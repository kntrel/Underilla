package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.generation.Phase;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.patch.Patcher;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Collects a finite set of chunk slices at selected generation points and publishes complete
 * frames on a caller-owned executor.
 *
 * <p>Calls to the patchers returned by this session may be concurrent. Chunk data is copied
 * before the session enqueues any rendering work, so platform-owned chunk data never escapes its
 * generation callback.</p>
 */
public final class Inspector {

    private final InspectionRegion region;
    private final Map<InspectionStage, FrameState> states;
    private final Executor renderExecutor;
    private final InspectionSink sink;
    private final CompletableFuture<InspectionReport> completion = new CompletableFuture<>();
    private final Map<InspectionStage, WorldSlice> completedFrames = new LinkedHashMap<>();
    private int renderedFrames;

    public Inspector(
            InspectionRegion region,
            Set<InspectionStage> stages,
            Executor renderExecutor,
            InspectionSink sink
    ) {
        this.region = Objects.requireNonNull(region, "region");
        Objects.requireNonNull(stages, "points");
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        this.renderExecutor = Objects.requireNonNull(renderExecutor, "renderExecutor");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.states = new LinkedHashMap<>();
        stages.stream()
                .sorted(Comparator.comparingInt(InspectionStage::order))
                .forEach(point -> states.put(
                        Objects.requireNonNull(point, "points must not contain null"),
                        new FrameState(newCanvas(region))));
    }

    /**
     * Decorates a completed generation plan with the selected before/after capture patchers.
     * The original plan remains unchanged.
     */
    public WorldGenerationPlan instrument(WorldGenerationPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new WorldGenerationPlan(
                plan.coverage(),
                decorate(plan.afterNoise(), Phase.NOISE),
                decorate(plan.afterSurface(), Phase.SURFACE),
                decorate(plan.afterCarvers(), Phase.CARVERS),
                decorate(plan.afterFeatures(), Phase.FEATURES),
                decorate(plan.afterLoad(), Phase.LOAD),
                plan.biomePatch(),
                plan.flags(),
                plan.altimeter());
    }

    /** Completes after every selected frame has been rendered and delivered to the sink. */
    public CompletionStage<InspectionReport> completion() {
        return completion;
    }

    /** Stops accepting chunk callbacks and completes the session as cancelled. */
    public synchronized void cancel() {
        completion.cancel(false);
    }

    private Patcher<ChunkData> decorate(Patcher<ChunkData> delegate, Phase phase) {
        return new InspectionPatcher(this, phase, delegate);
    }

    boolean includes(InspectionStage stage) {
        return states.containsKey(stage);
    }

    boolean contains(ChunkData chunk) {
        Objects.requireNonNull(chunk, "chunk");
        return region.contains(chunk.getChunkX(), chunk.getChunkZ());
    }

    void capture(InspectionStage point, ChunkData chunk) {
        try {
            Objects.requireNonNull(chunk, "chunk");
            if (!contains(chunk)) {
                return;
            }
            ChunkPosition position = new ChunkPosition(chunk.getChunkX(), chunk.getChunkZ());
            WorldSlice tile = WorldSlice.from(chunk, region);
            accept(point, position, tile);
        } catch (Throwable exception) {
            fail(exception);
        }
    }

    private synchronized void accept(InspectionStage stage, ChunkPosition position, WorldSlice tile) {
        if (completion.isDone()) {
            return;
        }
        FrameState state = states.get(stage);
        if (state == null || !state.received().add(position)) {
            return;
        }
        state.canvas().copyFrom(tile);
        if (state.received().size() != region.chunkLength()) {
            return;
        }
        WorldSlice frame = state.canvas().copy().freeze();
        completedFrames.put(stage, frame);
        try {
            CompletableFuture.runAsync(() -> sink.publish(stage, frame), renderExecutor)
                    .whenComplete((_, exception) -> rendered(stage, exception));
        } catch (Throwable exception) {
            fail(exception);
        }
    }

    private synchronized void rendered(InspectionStage point, Throwable exception) {
        if (completion.isDone()) {
            return;
        }
        if (exception != null) {
            fail(exception);
            return;
        }
        renderedFrames++;
        if (renderedFrames == states.size()) {
            completion.complete(new InspectionReport(completedFrames));
        }
    }

    private synchronized void fail(Throwable exception) {
        if (!completion.isDone()) {
            completion.completeExceptionally(exception);
        }
    }

    private static WorldSlice newCanvas(InspectionRegion region) {
        int chunkSize = GenerationConstants.CHUNK_SIZE;
        int offsetX = Math.multiplyExact(region.startChunk(), chunkSize);
        int endX = Math.multiplyExact(Math.addExact(region.startChunk(), region.chunkLength()), chunkSize);
        return new WorldSlice(offsetX, region.minimumY(), Math.subtractExact(endX, offsetX),
                Math.subtractExact(region.maximumY(), region.minimumY()));
    }

    private record FrameState(Set<ChunkPosition> received, WorldSlice canvas) {

        private FrameState(WorldSlice canvas) {
            this(new LinkedHashSet<>(), canvas);
        }
    }
}
