package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.*;
import com.kntrel.mc.underilla.core.api.scope.BiomeSnapshot;
import com.kntrel.mc.underilla.core.api.scope.DualBiomeScope;
import com.kntrel.mc.underilla.core.api.scope.ChunkSnapshot;
import com.kntrel.mc.underilla.core.patch.ChunkBlock;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.patch.PerBlockChunkPatcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

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
            GenerationArea generationArea,
            int maximumY,
            boolean useTopYOnly,
            WorldMask worldMask,
            Predicate<DualBiomeScope> surfaceOnlyBiome,
            Predicate<DualBiomeScope> preservedGeneratedBiome,
            boolean preserveGeneratedBiomesOnlyUnderSurface
    ) {
        Predicate<DualBiomeScope> shouldReplace = surfaceOnlyBiome.or(preservedGeneratedBiome.negate());
        return biome -> {
            if (!generationArea.contains(biome.getX(), biome.getZ())) {
                return;
            }
            int referenceY = useTopYOnly ? maximumY : biome.getY();
            Biome referenceBiome = referenceWorld.biomeAt(biome.getX(), referenceY, biome.getZ()).orElse(null);
            if (referenceBiome == null) { return; }



            ChunkSnapshot chunk = ChunkSnapshot.of(biome.getChunkX(), biome.getChunkZ());
            DualBiomeScope snapshot = DualBiomeScope.of(
                    chunk, referenceWorld, BiomeSnapshot.atBlock(referenceBiome, biome.getX(), referenceY, biome.getZ()),
                    biome.world(), BiomeSnapshot.atBlock(biome.get(), biome.getX(), biome.getY(), biome.getZ()));
            if (shouldReplace.test(snapshot)
                    || preserveGeneratedBiomesOnlyUnderSurface && biomeCellIntersectsMask(biome, worldMask)) {
                biome.set(referenceBiome);
            }
        };
    }

    private static boolean biomeCellIntersectsMask(BiomeData biome, WorldMask worldMask) {
        int cellSize = GenerationConstants.BIOME_CELL_SIZE;
        int cellX = biome.getBiomeX() * cellSize;
        int cellZ = biome.getBiomeZ() * cellSize;
        for (int x = cellX; x < cellX + cellSize; x++) {
            for (int z = cellZ; z < cellZ + cellSize; z++) {
                if (worldMask.contains(biome.world(), x, biome.getY(), z)) {
                    return true;
                }
            }
        }
        return false;
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
