package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.ID;

/** Read-only metadata for the target world being generated, independent of the selected source. */
public non-sealed interface World extends Entity {

    @Prop
    ID dimension();

    @Prop
    long seed();

    /** Inclusive lower build boundary. */
    @Prop
    int minY();

    /** Exclusive upper build boundary. */
    @Prop
    int maxY();
}
