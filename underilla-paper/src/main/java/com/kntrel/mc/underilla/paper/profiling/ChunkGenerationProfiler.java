package com.kntrel.mc.underilla.paper.profiling;

import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.profiling.Operation;
import com.kntrel.mc.underilla.core.profiling.Tracker;
import com.kntrel.mc.underilla.paper.generation.UnderillaChunkGenerator;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Correlates and times Underilla's separate generation callbacks for each chunk. */
public final class ChunkGenerationProfiler {

    static final String CHUNK_GENERATION_EVENT = "chunk_generation";

    private final Instrumenter instrumenter;
    private final Tracker tracker;
    private final LongSupplier nanoTime;
    private final ConcurrentMap<ChunkKey, ChunkProfile> profiles = new ConcurrentHashMap<>();

    public ChunkGenerationProfiler(Instrumenter instrumenter) {
        this(instrumenter, System::nanoTime);
    }

    ChunkGenerationProfiler(Instrumenter instrumenter, LongSupplier nanoTime) {
        this.instrumenter = Objects.requireNonNull(instrumenter, "instrumenter");
        this.tracker = instrumenter.tracker(UnderillaChunkGenerator.class);
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /** Runs one generation callback as part of the profile for the supplied chunk. */
    public void run(UUID worldId, int chunkX, int chunkZ, Runnable action) {
        Objects.requireNonNull(action, "action");
        call(worldId, chunkX, chunkZ, () -> {
            action.run();
            return null;
        });
    }

    /** Calls one generation callback as part of the profile for the supplied chunk. */
    public <T> T call(UUID worldId, int chunkX, int chunkZ, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        ChunkKey key = new ChunkKey(worldId, chunkX, chunkZ);
        ChunkProfile profile = enter(key);
        boolean failed = false;
        try {
            return profile.operation().call(action);
        } catch (RuntimeException | Error exception) {
            failed = true;
            throw exception;
        } finally {
            Completion completion = profile.exit(nanoTime.getAsLong(), failed);
            if (failed || completion != null) {
                profiles.remove(key, profile);
            }
            record(completion);
        }
    }

    /** Finishes and records the profile for a populated chunk. */
    public void complete(UUID worldId, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(worldId, chunkX, chunkZ);
        AtomicReference<Completion> completed = new AtomicReference<>();
        profiles.computeIfPresent(key, (_, profile) -> {
            Completion completion = profile.complete();
            completed.set(completion);
            return profile.isFinished() ? null : profile;
        });
        record(completed.get());
    }

    /** Discards unfinished chunk profiles, for example when the plugin shuts down. */
    public void discardAll() {
        profiles.forEach((_, profile) -> profile.discard());
        profiles.clear();
    }

    int activeProfiles() {
        return profiles.size();
    }

    private ChunkProfile enter(ChunkKey key) {
        while (true) {
            ChunkProfile profile = profiles.computeIfAbsent(
                    key,
                    _ -> new ChunkProfile(instrumenter.operation()));
            if (profile.enter(nanoTime.getAsLong())) {
                return profile;
            }
            profiles.remove(key, profile);
        }
    }

    private void record(Completion completion) {
        if (completion == null) {
            return;
        }
        completion.operation().run(() -> tracker.record(
                CHUNK_GENERATION_EVENT,
                Duration.ofNanos(completion.totalNanos())));
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
        private ChunkKey {
            Objects.requireNonNull(worldId, "worldId");
        }
    }

    private record Completion(Operation operation, long totalNanos) {}

    private static final class ChunkProfile {

        private final Operation operation;
        private int activeScopes;
        private long activeSinceNanos;
        private long totalNanos;
        private boolean completionRequested;
        private boolean finished;

        private ChunkProfile(Operation operation) {
            this.operation = operation;
        }

        private Operation operation() {
            return operation;
        }

        private synchronized boolean enter(long startedAtNanos) {
            if (finished) {
                return false;
            }
            if (activeScopes++ == 0) {
                activeSinceNanos = startedAtNanos;
            }
            return true;
        }

        private synchronized Completion exit(long stoppedAtNanos, boolean failed) {
            if (activeScopes <= 0) {
                throw new IllegalStateException("chunk profile exited without an active scope");
            }
            activeScopes--;
            if (activeScopes == 0) {
                totalNanos += stoppedAtNanos - activeSinceNanos;
            }
            if (failed) {
                finished = true;
                return null;
            }
            if (finished) {
                return null;
            }
            if (completionRequested && activeScopes == 0) {
                return finish();
            }
            return null;
        }

        private synchronized Completion complete() {
            if (finished) {
                return null;
            }
            completionRequested = true;
            return activeScopes == 0 ? finish() : null;
        }

        private synchronized boolean isFinished() {
            return finished;
        }

        private synchronized void discard() {
            finished = true;
        }

        private Completion finish() {
            finished = true;
            return new Completion(operation, totalNanos);
        }
    }
}
