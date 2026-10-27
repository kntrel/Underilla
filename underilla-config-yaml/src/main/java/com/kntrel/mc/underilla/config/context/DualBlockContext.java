package com.kntrel.mc.underilla.config.context;

import com.kntrel.mc.underilla.config.property.Prop;

/** Config-entity counterpart of core's DualBlockScope. */
public interface DualBlockContext extends DualBiomeContext {
    static DualBlockContext of(BlockContext source, BlockContext target) {
        return new Impl.DualBlockContextValue(source, target);
    }

    @Override @Prop BlockContext source();
    @Override @Prop BlockContext target();
}
