package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.World;

/**
 * One world's structure, sampled biome, chunk coordinates, and metadata for a decision.
 * The caller supplies the biome sampled at the chosen structure evaluation position.
 */
public interface StructureScope extends BiomeScope {

    static StructureScope of(Biome biome, StructureSnapshot structure) {
        return of(null, biome, structure);
    }

    static StructureScope of(World world, Biome biome, StructureSnapshot structure) {
        BiomeSnapshot sample = biome instanceof BiomeSnapshot snapshot ? snapshot
                : structure == null || structure.origin() == null ? BiomeSnapshot.of(biome)
                : BiomeSnapshot.atBlock(biome, structure.origin().x(), structure.origin().y(), structure.origin().z());
        return of(WorldSnapshot.of(world), sample, structure);
    }

    static StructureScope of(WorldSnapshot world, BiomeSnapshot biome, StructureSnapshot structure) {
        return of(world, null, biome, structure);
    }

    static StructureScope of(
            WorldSnapshot world, ChunkSnapshot chunk, BiomeSnapshot biome, StructureSnapshot structure
    ) {
        return new Impl.StructureSide(world, chunk, biome, structure);
    }

    /** {@return the supplied structure snapshot, or null when unavailable during this decision} */
    StructureSnapshot structure();
}
