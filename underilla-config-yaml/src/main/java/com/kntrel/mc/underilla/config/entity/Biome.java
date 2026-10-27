package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.BiomeSnapshot;
import java.util.Set;

/** Read-only biome sample, with biome-cell coordinates and externally supplied registry tags. */
public non-sealed interface Biome extends ConfigEntity, Identifiable, Taggable {
    static Biome of(BiomeSnapshot snapshot, Set<ID> tags) { return new Impl.BiomeValue(snapshot, tags); }

    @Prop ID id();
    @Prop default String namespace() { return id().namespace(); }
    @Prop default String name() { return id().value(); }
    @Prop Set<ID> tags();
    @Prop int cellX();
    @Prop int cellY();
    @Prop int cellZ();
}
