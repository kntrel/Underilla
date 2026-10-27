package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.WorldSnapshot;

/** Read-only metadata of the world supplied by the evaluation context. */
public non-sealed interface World extends ConfigEntity {
    static World of(WorldSnapshot snapshot) { return new Impl.WorldValue(snapshot); }

    @Prop ID dimension();
    @Prop long seed();
    /** Inclusive lower build boundary. */
    @Prop int minY();
    /** Exclusive upper build boundary. */
    @Prop int maxY();
}
