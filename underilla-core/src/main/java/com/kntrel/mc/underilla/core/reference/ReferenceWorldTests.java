package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.context.BlockContext;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Objects;
import java.util.function.Predicate;

/** Common tests for deciding whether a reference-world block should be written. */
final class ReferenceWorldTests {

    private ReferenceWorldTests() {}

    /** Selects reference blocks at target positions contained by the world mask. */
    public static Predicate<BlockContext> insideMask(WorldMask worldMask) {
        Objects.requireNonNull(worldMask, "worldMask");
        return ctx -> worldMask.contains(ctx.target().world(),
                ctx.target().block().x(), ctx.target().block().y(), ctx.target().block().z());
    }

    /** Selects surviving reference blocks only over solid generated terrain. */
    public static Predicate<BlockContext> survivingOutsideMask(Predicate<BlockContext> survivingBlock) {
        Objects.requireNonNull(survivingBlock, "survivingBlock");
        return survivingBlock.and(ctx -> ctx.target().block().isSolid());
    }
}
