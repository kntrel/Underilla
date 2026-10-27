package com.kntrel.mc.underilla.core.api.scope;

import com.kntrel.mc.underilla.core.api.Entity;
import com.kntrel.mc.underilla.core.api.ID;

/** Read-only type identity of one entity being evaluated. */
public record EntitySnapshot(ID id) {

    /** Captures an entity's current type identity, or null when the entity is unavailable. */
    public static EntitySnapshot of(Entity entity) {
        return entity == null ? null : new EntitySnapshot(entity.id());
    }
}
