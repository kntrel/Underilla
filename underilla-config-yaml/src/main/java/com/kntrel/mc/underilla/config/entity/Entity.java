package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.EntitySnapshot;
import java.util.Set;

/** Read-only entity candidate selected by the operation's evaluation contract. */
public non-sealed interface Entity extends ConfigEntity, Identifiable, Taggable {
    static Entity of(EntitySnapshot snapshot, Set<ID> tags) { return new Impl.EntityValue(snapshot, tags); }

    @Prop ID id();
    @Prop default String namespace() { return id().namespace(); }
    @Prop default String name() { return id().value(); }
    @Prop Set<ID> tags();
}
