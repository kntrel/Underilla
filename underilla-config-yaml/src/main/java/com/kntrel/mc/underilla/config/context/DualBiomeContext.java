package com.kntrel.mc.underilla.config.context;

import com.kntrel.mc.underilla.config.property.Prop;

/** Config-entity counterpart of core's DualBiomeScope. */
public interface DualBiomeContext extends DualWorldContext {
    static DualBiomeContext of(BiomeContext source, BiomeContext target) {
        return new Impl.DualBiomeContextValue(source, target);
    }

    @Override @Prop BiomeContext source();
    @Override @Prop BiomeContext target();
}
