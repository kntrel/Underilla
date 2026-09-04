package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.reader.EntityView;
import com.kntrel.mc.underilla.core.vector.IntVector;
import com.kntrel.mc.underilla.core.vector.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/** Runs a patcher against a chunk-data view that defers selected writes until after carvers. */
public final class DeferredPatcher implements ChunkPatcher {

    //FIELDS
    private final ChunkPatcher delegate;
    private final DeferredTasks deferredWrites;
    private final BiPredicate<Vector<Integer>, ChunkData> shouldDefer;
    private final Applier applier;


    //CONSTRUCTORS
    public DeferredPatcher(
            ChunkPatcher delegate,
            BiPredicate<Vector<Integer>, ChunkData> shouldDefer
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.deferredWrites = new DeferredTasks();
        this.shouldDefer = Objects.requireNonNull(shouldDefer, "shouldDefer");
        this.applier = new Applier(this.deferredWrites);
    }


    //API
    public ChunkPatcher applier() {
        return this.applier;
    }


    //IMPLEMENTATION
    @Override
    public void patch(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        delegate.patch(new ChunkDataProxy(targetChunk, deferredWrites, shouldDefer));
    }


    //HELPERS
    private static void defer(DeferredTasks tasks, ChunkData targetChunk, Consumer<ChunkData> task) {
        Objects.requireNonNull(task, "task");
        tasks.compute(coordinateOf(targetChunk), (ignored, currentTasks) -> {
            List<Consumer<ChunkData>> writes = currentTasks == null ? new ArrayList<>() : currentTasks;
            writes.add(task);
            return writes;
        });
    }
    private static List<Consumer<ChunkData>> remove(DeferredTasks tasks, ChunkData targetChunk) {
        AtomicReference<List<Consumer<ChunkData>>> removed = new AtomicReference<>(List.of());
        tasks.compute(coordinateOf(targetChunk), (ignored, currentTasks) -> {
            if (currentTasks != null) {
                removed.set(List.copyOf(currentTasks));
            }
            return null;
        });
        return removed.get();
    }
    private static ChunkCoordinate coordinateOf(ChunkData chunk) {
        return new ChunkCoordinate(chunk.getChunkX(), chunk.getChunkZ());
    }



    //SUBTYPES -----------------------------------------------------------------------------------------

    private record ChunkCoordinate(int x, int z) {}

    private record ChunkDataProxy(
            ChunkData delegate,
            DeferredTasks deferredWrites,
            BiPredicate<Vector<Integer>, ChunkData> shouldDefer
    ) implements ChunkData {

        private ChunkDataProxy {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(deferredWrites, "deferredWrites");
            Objects.requireNonNull(shouldDefer, "shouldDefer");
        }

        @Override
        public int getMaxHeight() {
            return delegate.getMaxHeight();
        }

        @Override
        public int getMinHeight() {
            return delegate.getMinHeight();
        }

        @Override
        public int getChunkX() {
            return delegate.getChunkX();
        }

        @Override
        public int getChunkZ() {
            return delegate.getChunkZ();
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return delegate.getBlock(x, y, z);
        }

        @Override
        public Biome getBiome(int x, int y, int z) {
            return delegate.getBiome(x, y, z);
        }

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Block material) {
            Objects.requireNonNull(material, "material");
            for (int x = xMin; x < xMax; x++) {
                for (int y = yMin; y < yMax; y++) {
                    for (int z = zMin; z < zMax; z++) {
                        setBlock(x, y, z, material);
                    }
                }
            }
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            Objects.requireNonNull(block, "block");
            IntVector position = new IntVector(x, y, z);
            if (shouldDefer.test(position, delegate)) {
                defer(deferredWrites, delegate, target -> target.setBlock(x, y, z, block));
            } else {
                delegate.setBlock(x, y, z, block);
            }
        }

        @Override
        public void setBiome(int x, int y, int z, Biome biome) {
            delegate.setBiome(x, y, z, biome);
        }

        @Override
        public void addEntity(EntityView entity) {
            delegate.addEntity(entity);
        }
    }

    private record Applier(DeferredTasks deferredWrites) implements ChunkPatcher {

        @Override
        public void patch(ChunkData targetChunk) {
            Objects.requireNonNull(targetChunk, "targetChunk");
            remove(deferredWrites, targetChunk).forEach(task -> task.accept(targetChunk));
        }
    }

    private static final class DeferredTasks extends ConcurrentHashMap<ChunkCoordinate, List<Consumer<ChunkData>>> {}
}
