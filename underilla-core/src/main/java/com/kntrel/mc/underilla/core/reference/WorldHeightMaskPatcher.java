package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.patch.ChunkPatcher;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/** Runs a patcher with a temporary mask selecting positions above the target chunk's surface. */
public final class WorldHeightMaskPatcher implements ChunkPatcher {

    private final int minimumY;
    private final TempChunkHeightMask mask;
    private final ChunkPatcher delegate;

    public WorldHeightMaskPatcher(int minimumY, Function<WorldMask, ChunkPatcher> delegateFactory) {
        this.minimumY = minimumY;
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

    private ChunkHeightMap calculateHeightMap(ChunkData chunk) {
        short[][] heights = new short[GenerationConstants.CHUNK_SIZE][GenerationConstants.CHUNK_SIZE];
        for (int x = 0; x < GenerationConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < GenerationConstants.CHUNK_SIZE; z++) {
                heights[x][z] = (short) heightAt(chunk, x, z);
            }
        }
        return new ChunkHeightMap(chunk.getChunkX(), chunk.getChunkZ(), heights);
    }

    private int heightAt(ChunkData chunk, int x, int z) {
        for (int y = chunk.getMaxHeight() - 1; y >= Math.max(minimumY, chunk.getMinHeight()); y--) {
            Block block = chunk.getBlock(x, y, z);
            if (block != null && block.isSolid()) {
                return y;
            }
        }
        return minimumY;
    }

    private record ChunkHeightMap(int x, int z, short[][] heights) {}

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
            if (heightMap == null) {
                return false;
            }
            int localX = Math.floorMod(globalX, GenerationConstants.CHUNK_SIZE);
            int localZ = Math.floorMod(globalZ, GenerationConstants.CHUNK_SIZE);
            return y > heightMap.heights[localX][localZ];
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
