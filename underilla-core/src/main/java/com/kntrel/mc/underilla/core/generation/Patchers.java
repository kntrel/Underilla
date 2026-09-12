package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.BlockFactory;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.patch.ChunkBlock;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.patch.PerBlockChunkPatcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

class Patchers {

    private Patchers() {}

    static Patcher<ChunkData> referenceWorldEntityPatcher(WorldReader referenceWorld) {
        Objects.requireNonNull(referenceWorld, "referenceWorld");
        return targetChunk -> {
            Objects.requireNonNull(targetChunk, "targetChunk");
            referenceWorld.readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ())
                    .map(ChunkReader::getEntities)
                    .ifPresent(entities -> entities.forEach(targetChunk::addEntity));
        };
    }

    static Patcher<BiomeData> referenceWorldBiomePatcher(
            WorldReader referenceWorld,
            Predicate<BiomeData> included,
            ToIntFunction<BiomeData> referenceY,
            BiPredicate<BiomeData, Biome> shouldReplace
    ) {
        Patcher<BiomeData> copyReferenceBiome = biome -> {
            referenceWorld.biomeAt(biome.getX(), referenceY.applyAsInt(biome), biome.getZ())
                    .filter(referenceBiome -> shouldReplace.test(biome, referenceBiome))
                    .ifPresent(biome::set);
        };
        return Patcher.<BiomeData>iff(included)
                .then(copyReferenceBiome)
                .end();
    }

    /** Applies configured support and replacement rules to every block in a chunk. */
    static Patcher<ChunkData> blockCleanupPatcher(
            BlockFactory blocks,
            Function<ID, Optional<ID>> supportReplacement,
            Function<ID, Optional<ID>> blockReplacement
    ) {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(supportReplacement, "supportReplacement");
        Objects.requireNonNull(blockReplacement, "blockReplacement");

        Patcher<ChunkBlock> unsupportedBlock = Patcher.<ChunkBlock>iff(block ->
                        block.y() > block.targetChunk().getMinHeight()
                        && !block.initialBlock().isAir()
                        && !block.targetChunk().getBlock(block.x(), block.y() - 1, block.z()).isSolid())
                .then(block -> supportReplacement.apply(block.initialBlock().id())
                        .ifPresent(id -> block.replace(blocks.create(id))))
                .end();
        Patcher<ChunkBlock> replacement = block -> blockReplacement.apply(block.initialBlock().id())
                .ifPresent(id -> block.replace(blocks.create(id)));
        return new PerBlockChunkPatcher(unsupportedBlock, replacement);
    }

    /** Builds the one-pass reference-world block pipeline without installing it in a plan. */
    static Patcher<ChunkData> referenceWorldPatcher(
            WorldReader referenceWorld,
            WorldMask worldMask,
            int minimumY,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            Collection<Patcher<ChunkBlock>> transformations
    ) {
        Objects.requireNonNull(referenceWorld, "referenceWorld");
        Objects.requireNonNull(worldMask, "worldMask");
        Objects.requireNonNull(air, "air");

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
        return Patcher.<ChunkData>iff(targetChunk -> referenceWorld
                        .readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ())
                        .isPresent())
                .then(chunkPatch)
                .end();
    }

    static Patcher<ChunkData> dualReferenceWorldPatcher(
            WorldReader positiveWorld,
            WorldReader negativeWorld,
            WorldMask worldMask,
            int minimumY,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            Collection<Patcher<ChunkBlock>> transformations
    ) {
        Objects.requireNonNull(negativeWorld, "negativeWorld");
        Patcher<ChunkBlock> surface = referenceWorldBlockPatcher(
                positiveWorld,
                worldMask,
                air,
                survivingBlock,
                transformations
        );
        Patcher<ChunkBlock> underground = referenceWorldBlockPatcher(
                negativeWorld,
                worldMask.inverted(),
                air,
                null,
                null
        );
        Patcher<ChunkData> surfacePatch = perBlock(minimumY, surface);
        Patcher<ChunkData> undergroundPatch = perBlock(minimumY, underground);
        Patcher<ChunkData> both = perBlock(minimumY, underground, surface);

        return Patcher.<ChunkData>iff(chunk -> chunkExists(positiveWorld, chunk))
                .then(Patcher.<ChunkData>iff(chunk -> chunkExists(negativeWorld, chunk))
                        .then(both)
                        .otherwise(surfacePatch)
                        .end())
                .otherwise(Patcher.<ChunkData>iff(chunk -> chunkExists(negativeWorld, chunk))
                        .then(undergroundPatch)
                        .end())
                .end();
    }

    @SafeVarargs
    private static Patcher<ChunkData> perBlock(int minimumY, Patcher<ChunkBlock>... patchers) {
        Patcher<ChunkBlock> aboveMinimumY = Patcher.<ChunkBlock>iff(block -> block.y() >= minimumY)
                .then(Patcher.sequence(patchers))
                .end();
        return new PerBlockChunkPatcher(aboveMinimumY);
    }

    private static boolean chunkExists(WorldReader world, ChunkData chunk) {
        return world.readChunk(chunk.getChunkX(), chunk.getChunkZ()).isPresent();
    }

    private static Patcher<ChunkBlock> referenceWorldBlockPatcher(
            WorldReader referenceWorld,
            WorldMask worldMask,
            Supplier<Block> air,
            Predicate<Block> survivingBlock,
            Collection<Patcher<ChunkBlock>> transformations
    ) {
        Function<ChunkBlock, Block> referenceBlock = target -> referenceWorld
                .blockAt(target.globalX(), target.y(), target.globalZ())
                .orElseGet(air);
        Predicate<ChunkBlock> shouldWrite = candidate -> worldMask.contains(
                candidate.globalX(), candidate.y(), candidate.globalZ());
        if (survivingBlock != null) {
            shouldWrite = shouldWrite.or(candidate ->
                    survivingBlock.test(candidate.candidate()) && candidate.destinationBlock().isSolid());
        }

        var candidate = Patcher.<ChunkBlock>take(original -> original.attempt(referenceBlock.apply(original)));
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
