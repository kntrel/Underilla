package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.patch.CandidatePatcher;
import com.kntrel.mc.underilla.core.patch.ChunkBlock;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.patch.PerBlockChunkPatcher;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.reference.WorldHeightMaskPatcher;
import com.kntrel.mc.underilla.core.reference.mask.UnionWorldMask;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

class Patchers {

    private Patchers() {}

    /** Applies configured support and replacement rules to every block in a chunk. */
    static Patcher<ChunkData> blockCleanupPatcher(
            BlockFactory blocks,
            Function<ID, Optional<ID>> supportReplacement,
            Function<ID, Optional<ID>> blockReplacement
    ) {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(supportReplacement, "supportReplacement");
        Objects.requireNonNull(blockReplacement, "blockReplacement");

        Patcher<ChunkBlock> unsupportedBlock =
                Patcher.<ChunkBlock>iff(block ->    block.y() > block.targetChunk().getMinHeight()
                                                            && !block.initialBlock().isAir()
                                                            && !block.targetChunk().getBlock(block.x(), block.y() - 1, block.z()).isSolid())
                                    .then(block -> supportReplacement.apply(block.initialBlock().id()).ifPresent(id -> block.replace(blocks.create(id))))
                                    .end();
        Patcher<ChunkBlock> replacement = block -> blockReplacement.apply(block.initialBlock().id()).ifPresent(id -> block.replace(blocks.create(id)));
        return new PerBlockChunkPatcher(unsupportedBlock, replacement);
    }

    /** Builds the one-pass reference-world block pipeline without installing it in a plan. */
    static Patcher<ChunkData> referenceWorldPatcher(
            WorldReader referenceWorld,
            WorldMask worldMask,
            boolean surfaceFill,
            int minimumY,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            Collection<Patcher<ChunkBlock>> transformations
    ) {
        Objects.requireNonNull(referenceWorld, "referenceWorld");
        Objects.requireNonNull(worldMask, "worldMask");
        Objects.requireNonNull(air, "air");

        if (surfaceFill) {
            return new WorldHeightMaskPatcher(minimumY, heightMask -> referenceWorldPatcher(
                    referenceWorld,
                    new UnionWorldMask(heightMask, worldMask),
                    false,
                    minimumY,
                    air,
                    survivingBlock,
                    transformations
            ));
        }

        Patcher<ChunkBlock> blockPatch = Patcher.<ChunkBlock>iff(block -> block.y() >= minimumY)
                .then(referenceWorldBlockPatcher(
                        referenceWorld,
                        worldMask,
                        air,
                        survivingBlock,
                        transformations
                ))
                .end();
        Patcher<ChunkData> chunkPatch = new PerBlockChunkPatcher(blockPatch);
        return Patcher.<ChunkData>iff(targetChunk -> referenceWorld.readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ()).isPresent())
                .then(chunkPatch)
                .end();
    }

    static Patcher<ChunkBlock> referenceWorldBlockPatcher(
            WorldReader referenceWorld,
            WorldMask worldMask,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            Collection<Patcher<ChunkBlock>> transformations
    ) {
        Function<ChunkBlock, Block> referenceBlock = target -> referenceWorld.blockAt(target.globalX(), target.y(), target.globalZ()).orElseGet(air);
        Predicate<ChunkBlock> shouldWrite = candidate -> worldMask.contains(candidate.globalX(), candidate.y(), candidate.globalZ());
        if (survivingBlock != null) {
            shouldWrite = shouldWrite.or(candidate -> survivingBlock.test(candidate.candidate()) && candidate.destinationBlock().isSolid());
        }

        CandidatePatcher.Builder<ChunkBlock> candidate = Patcher.take(original -> original.attempt(referenceBlock.apply(original)));
        if (transformations != null && !transformations.isEmpty()) {
            candidate.patch(transformations);
        }
        Predicate<ChunkBlock> finalShouldWrite = shouldWrite;
        return candidate
                .iff((_, proposed) -> finalShouldWrite.test(proposed))
                .then((original, proposed) -> original.replace(proposed.candidate()))
                .end();
    }
}
