package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.GenerationConstants;

/** Read-only location and source coverage for a target chunk. Coordinates are chunk coordinates. */
public non-sealed interface Chunk extends Entity {

    @Prop
    World world();

    @Prop
    int x();

    @Prop
    int z();

    /** Inclusive global block X boundary. */
    @Prop
    default int minX() { return x() * GenerationConstants.CHUNK_SIZE; }

    /** Inclusive global block Z boundary. */
    @Prop
    default int minZ() { return z() * GenerationConstants.CHUNK_SIZE; }

    /** Exclusive global block X boundary. */
    @Prop
    default int maxX() { return minX() + GenerationConstants.CHUNK_SIZE; }

    /** Exclusive global block Z boundary. */
    @Prop
    default int maxZ() { return minZ() + GenerationConstants.CHUNK_SIZE; }
}
