package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.BlockSnapshot;
import java.util.Map;
import java.util.Set;

/** Read-only block candidate with global coordinates and supplied tag/state metadata. */
public non-sealed interface Block extends ConfigEntity, Identifiable, Taggable {
    static Block of(BlockSnapshot snapshot, Set<ID> tags, boolean waterlogged, Map<String, ?> state) {
        return new Impl.BlockValue(snapshot, tags, waterlogged, state);
    }

    @Prop ID id();
    @Prop default String namespace() { return id().namespace(); }
    @Prop default String name() { return id().value(); }
    @Prop Set<ID> tags();
    @Prop int x();
    @Prop int y();
    @Prop int z();
    @Prop default int localX() { return Math.floorMod(x(), GenerationConstants.CHUNK_SIZE); }
    @Prop default int localZ() { return Math.floorMod(z(), GenerationConstants.CHUNK_SIZE); }
    @Prop boolean air();
    @Prop boolean solid();
    @Prop boolean waterloggable();
    @Prop boolean waterlogged();
    /** Unmodifiable state values: Boolean, Integer, or String. */
    @Prop Map<String, ?> state();
}
