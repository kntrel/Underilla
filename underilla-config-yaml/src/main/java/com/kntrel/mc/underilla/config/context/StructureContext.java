package com.kntrel.mc.underilla.config.context;

import com.kntrel.mc.underilla.config.entity.World;
import com.kntrel.mc.underilla.config.entity.Chunk;
import com.kntrel.mc.underilla.config.entity.Biome;
import com.kntrel.mc.underilla.config.entity.Structure;
import com.kntrel.mc.underilla.config.property.Prop;

/** One side's structure candidate and sampled biome/world data. Unavailable values may be null. */
public interface StructureContext extends BiomeContext {
    static StructureContext of(World world, Chunk chunk, Biome biome, Structure structure) {
        return new Impl.StructureContextValue(world, chunk, biome, structure);
    }

    @Prop Structure structure();
}
