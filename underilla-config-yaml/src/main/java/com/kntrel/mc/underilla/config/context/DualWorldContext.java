package com.kntrel.mc.underilla.config.context;

import com.kntrel.mc.underilla.config.property.Prop;

/** Config-entity counterpart of core's DualWorldScope. */
public interface DualWorldContext {
    static DualWorldContext of(WorldContext source, WorldContext target) {
        return new Impl.DualWorldContextValue(source, target);
    }

    @Prop WorldContext source();
    @Prop WorldContext target();
}
