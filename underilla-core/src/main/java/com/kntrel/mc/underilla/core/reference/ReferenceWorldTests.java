package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.scope.DualBlockScope;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Objects;
import java.util.function.Predicate;

/** Common tests for deciding whether a reference-world block should be written. */
final class ReferenceWorldTests {

    private ReferenceWorldTests() {}

    /** Selects reference blocks at target positions contained by the world mask. */
    public static Predicate<DualBlockScope> insideMask(WorldMask worldMask) {
        Objects.requireNonNull(worldMask, "worldMask");
        return snapshot -> worldMask.contains(snapshot.target().world(),
                snapshot.target().block().x(), snapshot.target().block().y(), snapshot.target().block().z());
    }

    /** Selects surviving reference blocks only over solid generated terrain. */
    public static Predicate<DualBlockScope> survivingOutsideMask(Predicate<DualBlockScope> survivingBlock) {
        Objects.requireNonNull(survivingBlock, "survivingBlock");
        return survivingBlock.and(snapshot -> snapshot.target().block().isSolid());
    }
}
