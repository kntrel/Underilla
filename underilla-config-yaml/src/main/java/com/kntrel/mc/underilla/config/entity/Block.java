package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.Map;
import java.util.Set;

/**
 * Read-only block candidate selected by the operation's evaluation contract.
 * Coordinates are global block coordinates. Implementations expose no mutation operations.
 */
public non-sealed interface Block extends Entity {

    /** Biome sample associated with this candidate by the evaluation contract. */
    @Prop
    Biome biome();

    @Prop
    default Chunk chunk() { return biome().chunk(); }

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
    int x();

    @Prop
    int y();

    @Prop
    int z();

    /** Chunk-local X coordinate, including for negative global coordinates. */
    @Prop
    default int localX() { return Math.floorMod(x(), GenerationConstants.CHUNK_SIZE); }

    /** Chunk-local Z coordinate, including for negative global coordinates. */
    @Prop
    default int localZ() { return Math.floorMod(z(), GenerationConstants.CHUNK_SIZE); }

    @Prop
    boolean air();

    /** Solid classification used by terrain processing. */
    @Prop
    boolean solid();

    /** Whether this block supports a waterlogged state. */
    @Prop
    boolean waterloggable();

    /** Current waterlogged state; false for blocks that do not support waterlogging. */
    @Prop
    boolean waterlogged();

    /**
     * Unmodifiable map of state names to non-null typed values: {@link Boolean}, {@link Integer},
     * or {@link String} for named states. Unsupported state properties are absent from the map.
     */
    @Prop
    Map<String, ?> state();
}
