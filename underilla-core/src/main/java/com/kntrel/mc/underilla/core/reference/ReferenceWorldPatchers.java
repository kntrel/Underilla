package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.cache.ChunkCache;
import com.kntrel.mc.underilla.core.cache.TopicChunkCache;
import com.kntrel.mc.underilla.core.generation.NoodleCavesPolicy;
import com.kntrel.mc.underilla.core.patch.ChunkBlock;
import com.kntrel.mc.underilla.core.patch.PatchTimeValue;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.patch.PerBlockChunkPatcher;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.reference.mask.UnionWorldMask;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Builds the surface and post-carver phases for copying the positive reference world and its
 * optional negative world. Source selection and write timing are decided together per block.
 */
public final class ReferenceWorldPatchers {

    private final Patcher<ChunkData> afterSurface;
    private final Patcher<ChunkData> afterCarvers;

    private ReferenceWorldPatchers(Patcher<ChunkData> afterSurface, Patcher<ChunkData> afterCarvers) {
        this.afterSurface = Objects.requireNonNull(afterSurface, "afterSurface");
        this.afterCarvers = Objects.requireNonNull(afterCarvers, "afterCarvers");
    }

    public Patcher<ChunkData> afterSurface() {
        return afterSurface;
    }

    public Patcher<ChunkData> afterCarvers() {
        return afterCarvers;
    }

    public static ReferenceWorldPatchers from(
            WorldReader referenceWorld,
            WorldReader negativeWorld,
            WorldMask worldMask,
            boolean surfaceFill,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            Collection<Patcher<ChunkBlock>> transformations,
            NoodleCavesPolicy policy,
            boolean useTopYOnly,
            ChunkCache cache
    ) {
        Objects.requireNonNull(referenceWorld, "referenceWorld");
        Objects.requireNonNull(worldMask, "worldMask");
        Objects.requireNonNull(air, "air");
        List<Patcher<ChunkBlock>> blockTransformations = List.copyOf(
                Objects.requireNonNull(transformations, "transformations"));
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(cache, "cache");

        return switch (policy) {
            case NoodleCavesPolicy.Underground _ -> undergroundPhases(
                    referenceWorld,
                    negativeWorld,
                    worldMask,
                    surfaceFill,
                    air,
                    survivingBlock,
                    blockTransformations
            );
            case NoodleCavesPolicy.Surface surface -> surfacePhases(
                    referenceWorld,
                    negativeWorld,
                    worldMask,
                    surfaceFill,
                    air,
                    survivingBlock,
                    blockTransformations,
                    surface,
                    useTopYOnly,
                    cache
            );
        };
    }

    private static ReferenceWorldPatchers undergroundPhases(
            WorldReader referenceWorld,
            WorldReader negativeWorld,
            WorldMask worldMask,
            boolean surfaceFill,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            List<Patcher<ChunkBlock>> transformations
    ) {
        Patcher<ChunkData> afterCarvers = withEffectiveMask(
                worldMask,
                surfaceFill,
                mask -> referenceWorldPatcher(
                        referenceWorld,
                        negativeWorld,
                        mask,
                        air,
                        survivingBlock,
                        transformations,
                        ReferenceWorldPatchers::adopt
                )
        );
        return new ReferenceWorldPatchers(_ -> {}, afterCarvers);
    }

    private static ReferenceWorldPatchers surfacePhases(
            WorldReader referenceWorld,
            WorldReader negativeWorld,
            WorldMask worldMask,
            boolean surfaceFill,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            List<Patcher<ChunkBlock>> transformations,
            NoodleCavesPolicy.Surface policy,
            boolean useTopYOnly,
            ChunkCache cache
    ) {
        TopicChunkCache<DeferredBlockWrites> deferredWrites = cache.topicView(DeferredBlockWrites.class);
        PatchTimeValue<ChunkData, DeferredBlockWrites> currentWrites = Patcher.value(_ -> new DeferredBlockWrites());
        Predicate<ChunkBlock> shouldDefer = shouldDefer(referenceWorld, policy, useTopYOnly);

        Patcher<ChunkData> collect = withEffectiveMask(
                worldMask,
                surfaceFill,
                mask -> referenceWorldPatcher(
                        referenceWorld,
                        negativeWorld,
                        mask,
                        air,
                        survivingBlock,
                        transformations,
                        (original, proposed, insideMask) -> {
                            if (insideMask && shouldDefer.test(proposed)) {
                                currentWrites.get().add(proposed);
                            } else {
                                original.adopt(proposed);
                            }
                        }
                )
        );
        Patcher<ChunkData> afterSurface = Patcher.with(currentWrites).sequence(
                collect,
                targetChunk -> deferredWrites.put(
                        targetChunk.getChunkX(), targetChunk.getChunkZ(), currentWrites.get())
        );

        Patcher<ChunkData> recompute = withEffectiveMask(
                worldMask,
                surfaceFill,
                mask -> referenceWorldPatcher(
                        referenceWorld,
                        null,
                        mask,
                        air,
                        survivingBlock,
                        transformations,
                        (original, proposed, insideMask) -> {
                            if (insideMask && shouldDefer.test(proposed)) {
                                original.adopt(proposed);
                            }
                        }
                )
        );
        Patcher<ChunkData> afterCarvers = targetChunk -> deferredWrites
                .take(targetChunk.getChunkX(), targetChunk.getChunkZ())
                .ifPresentOrElse(
                        writes -> writes.applyTo(targetChunk),
                        () -> recompute.patch(targetChunk)
                );
        return new ReferenceWorldPatchers(afterSurface, afterCarvers);
    }

    private static Patcher<ChunkData> withEffectiveMask(
            WorldMask worldMask,
            boolean surfaceFill,
            Function<WorldMask, Patcher<ChunkData>> patcher
    ) {
        if (!surfaceFill) {
            return patcher.apply(worldMask);
        }
        return new WorldHeightMaskPatcher(heightMask -> patcher.apply(new UnionWorldMask(heightMask, worldMask)));
    }

    private static Patcher<ChunkData> referenceWorldPatcher(
            WorldReader referenceWorld,
            WorldReader negativeWorld,
            WorldMask worldMask,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            List<Patcher<ChunkBlock>> transformations,
            AcceptedWrite acceptedWrite
    ) {
        Patcher<ChunkBlock> reference = perBlock(
                referenceWorld,
                worldMask,
                air,
                survivingBlock,
                transformations,
                acceptedWrite
        );
        Patcher<ChunkData> surfaceOnly = Patcher
                .<ChunkData>iff(chunk -> chunkExists(referenceWorld, chunk))
                .then(perBlock(reference))
                .end();
        if (negativeWorld == null) {
            return surfaceOnly;
        }

        Patcher<ChunkBlock> negative = perBlock(
                negativeWorld,
                worldMask.inverted(),
                air,
                null,
                List.of(),
                ReferenceWorldPatchers::adopt
        );
        Patcher<ChunkData> both = perBlock(negative, reference);
        Patcher<ChunkData> negativeOnly = perBlock(negative);

        return Patcher
                .<ChunkData>iff(chunk -> chunkExists(negativeWorld, chunk))
                .then(Patcher.<ChunkData>iff(chunk -> chunkExists(referenceWorld, chunk))
                        .then(both)
                        .otherwise(negativeOnly)
                        .end())
                .otherwise(surfaceOnly)
                .end();
    }

    @SafeVarargs
    private static Patcher<ChunkData> perBlock(Patcher<ChunkBlock>... patchers) {
        return new PerBlockChunkPatcher(patchers.length == 1 ? patchers[0] : Patcher.sequence(patchers));
    }

    private static boolean chunkExists(WorldReader world, ChunkData chunk) {
        return world.readChunk(chunk.getChunkX(), chunk.getChunkZ()).isPresent();
    }

    private static Patcher<ChunkBlock> perBlock(
            WorldReader referenceWorld,
            WorldMask worldMask,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            List<Patcher<ChunkBlock>> transformations,
            AcceptedWrite acceptedWrite
    ) {
        return original -> {
            Block referenceBlock = referenceWorld
                    .blockAt(original.globalX(), original.y(), original.globalZ())
                    .orElseGet(air);
            ChunkBlock proposed = original.attempt(referenceBlock);
            transformations.forEach(transformation -> transformation.patch(proposed));

            boolean insideMask = worldMask.contains(original.globalX(), original.y(), original.globalZ());
            boolean survivesOutsideMask = survivingBlock != null
                    && survivingBlock.test(proposed.candidate())
                    && proposed.destinationBlock().isSolid();
            if (insideMask || survivesOutsideMask) {
                acceptedWrite.accept(original, proposed, insideMask);
            }
        };
    }

    private static Predicate<ChunkBlock> shouldDefer(
            WorldReader referenceWorld,
            NoodleCavesPolicy.Surface policy,
            boolean useTopYOnly
    ) {
        Predicate<ChunkBlock> mayWriteBeforeCarvers = block -> referenceWorld
                .biomeAt(
                        block.globalX(),
                        useTopYOnly ? block.targetChunk().getMaxHeight() : block.y(),
                        block.globalZ())
                .filter(policy.predicate())
                .isPresent();
        if (policy.restoreLiquids()) {
            mayWriteBeforeCarvers = mayWriteBeforeCarvers.and(block -> referenceWorld
                    .blockAt(block.globalX(), block.y(), block.globalZ())
                    .map(Block::isLiquid)
                    .map(isLiquid -> !isLiquid)
                    .orElse(true));
        }
        return mayWriteBeforeCarvers.negate();
    }

    private static void adopt(ChunkBlock original, ChunkBlock proposed, boolean insideMask) {
        original.adopt(proposed);
    }

    @FunctionalInterface
    private interface AcceptedWrite {
        void accept(ChunkBlock original, ChunkBlock proposed, boolean insideMask);
    }

    private static final class DeferredBlockWrites {

        private static final int INITIAL_CAPACITY = 256;

        private int[] xCoordinates = new int[0];
        private int[] yCoordinates = new int[0];
        private int[] zCoordinates = new int[0];
        private Block[] blocks = new Block[0];
        private int size;

        private void add(ChunkBlock block) {
            ensureCapacity(size + 1);
            xCoordinates[size] = block.x();
            yCoordinates[size] = block.y();
            zCoordinates[size] = block.z();
            blocks[size] = block.candidate();
            size++;
        }

        private void applyTo(ChunkData targetChunk) {
            for (int index = 0; index < size; index++) {
                targetChunk.setBlock(
                        xCoordinates[index],
                        yCoordinates[index],
                        zCoordinates[index],
                        blocks[index]
                );
            }
        }

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity <= blocks.length) {
                return;
            }
            int newCapacity = blocks.length == 0
                    ? Math.max(requiredCapacity, INITIAL_CAPACITY)
                    : Math.max(requiredCapacity, blocks.length * 2);
            xCoordinates = Arrays.copyOf(xCoordinates, newCapacity);
            yCoordinates = Arrays.copyOf(yCoordinates, newCapacity);
            zCoordinates = Arrays.copyOf(zCoordinates, newCapacity);
            blocks = Arrays.copyOf(blocks, newCapacity);
        }
    }
}
