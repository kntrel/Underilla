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
import java.util.Objects;
import java.util.Optional;
import java.util.function.*;

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
    static PerBlockChunkPatcher blockCleanupPatcher(
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

    static PerBlockChunkPatcher illegalBlockPatcher(BlockFactory blocks, Function<ID, Optional<ID>> replacement) {
        return new PerBlockChunkPatcher(b -> {
            Block block = b.block();
            if (block.isAir()) { return; }
            if (block.isLegal()) { return; }

            Block replacementBlock = replacement.apply(block.id()).map(blocks::create).orElseGet(blocks::air);
            b.replace(replacementBlock);
        });
    }
}
