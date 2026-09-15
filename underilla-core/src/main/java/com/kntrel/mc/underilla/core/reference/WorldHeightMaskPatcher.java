package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/** Runs a patcher with a temporary mask selecting positions above the target chunk's surface. */
public final class WorldHeightMaskPatcher implements Patcher<ChunkData> {

    private final TempChunkHeightMask mask;
    private final Patcher<ChunkData> delegate;

    public WorldHeightMaskPatcher(Function<WorldMask, Patcher<ChunkData>> delegateFactory) {
        this.mask = new TempChunkHeightMask();
        this.delegate = Objects.requireNonNull(delegateFactory, "delegateFactory").apply(mask);
        Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        ChunkHeightMap heightMap = calculateHeightMap(targetChunk);
        try (TempChunkHeightMask.Lease _ = mask.install(heightMap)) {
            delegate.patch(targetChunk);
        }
    }

    /** Captures the target chunk's current solid-surface mask for use during one patch invocation. */
    public static WorldMask snapshot(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        ChunkHeightMap heightMap = calculateHeightMap(targetChunk);
        return heightMap::contains;
    }

    private static ChunkHeightMap calculateHeightMap(ChunkData chunk) {
        short[][] heights = new short[GenerationConstants.CHUNK_SIZE][GenerationConstants.CHUNK_SIZE];
        for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                heights[x][z] = (short) heightAt(chunk, x, z);
            }
        }
        return new ChunkHeightMap(chunk.getChunkX(), chunk.getChunkZ(), heights);
    }

    private static int heightAt(ChunkData chunk, int x, int z) {
        for (int y = chunk.getMaxHeight() - 1; y >= chunk.getMinHeight(); y--) {
            Block block = chunk.getBlock(x, y, z);
            if (block != null && block.isSolid()) {
                return y;
            }
        }
        return chunk.getMinHeight();
    }

    private record ChunkHeightMap(int x, int z, short[][] heights) {

        private boolean contains(int globalX, int y, int globalZ) {
            int size = GenerationConstants.CHUNK_SIZE;
            if (Math.floorDiv(globalX, size) != x || Math.floorDiv(globalZ, size) != z) {
                return false;
            }
            int localX = Math.floorMod(globalX, size);
            int localZ = Math.floorMod(globalZ, size);
            return y > heights[localX][localZ];
        }
    }

    private record ChunkCoordinate(int x, int z) {}



    private static final class TempChunkHeightMask implements WorldMask {

        private final ConcurrentHashMap<ChunkCoordinate, Slot> slots = new ConcurrentHashMap<>();

        private Lease install(ChunkHeightMap heightMap) {
            ChunkCoordinate coordinate = new ChunkCoordinate(heightMap.x, heightMap.z);
            Slot slot = slots.compute(coordinate, (_, current) -> {
                Slot result = current == null ? new Slot() : current;
                result.users.incrementAndGet();
                return result;
            });
            slot.lock.lock();
            slot.heightMap = heightMap;
            return new Lease(coordinate, slot);
        }

        @Override
        public boolean contains(int globalX, int y, int globalZ) {
            ChunkCoordinate coordinate = new ChunkCoordinate(
                    Math.floorDiv(globalX, GenerationConstants.CHUNK_SIZE),
                    Math.floorDiv(globalZ, GenerationConstants.CHUNK_SIZE)
            );
            Slot slot = slots.get(coordinate);
            ChunkHeightMap heightMap = slot == null ? null : slot.heightMap;
            return heightMap != null && heightMap.contains(globalX, y, globalZ);
        }

        private static final class Slot {
            private final ReentrantLock lock = new ReentrantLock();
            private final AtomicInteger users = new AtomicInteger();
            private volatile ChunkHeightMap heightMap;
        }

        private final class Lease implements AutoCloseable {

            private final ChunkCoordinate coordinate;
            private final Slot slot;
            private boolean closed;

            private Lease(ChunkCoordinate coordinate, Slot slot) {
                this.coordinate = coordinate;
                this.slot = slot;
            }

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                slot.heightMap = null;
                slot.lock.unlock();
                slots.computeIfPresent(coordinate, (_, current) -> {
                    if (current != slot || slot.users.decrementAndGet() > 0) {
                        return current;
                    }
                    return null;
                });
            }
        }
    }
}
