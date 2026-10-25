package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.Set;

/**
 * Read-only biome sample selected by the operation's evaluation contract.
 * Cell coordinates identify the sampled biome cell, rather than global block coordinates.
 */
public non-sealed interface Biome extends Entity {

    @Prop
    Chunk chunk();

    @Prop
    default World world() { return chunk().world(); }

    @Prop
    ID identifier();

    @Prop
    default String namespace() { return identifier().namespace(); }

    @Prop
    default String name() { return identifier().value(); }


    /** Unmodifiable set of registry tag identifiers, without a leading {@code #}. */
    @Prop
    Set<ID> tags();

    @Prop
    int cellX();

    @Prop
    int cellY();

    @Prop
    int cellZ();
}
