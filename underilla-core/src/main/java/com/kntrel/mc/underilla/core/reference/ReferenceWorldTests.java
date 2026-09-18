package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.reference.mask.WorldMask;
import java.util.Objects;
import java.util.function.Predicate;

/** Common tests for deciding whether a reference-world block should be written. */
public final class ReferenceWorldTests {

    private ReferenceWorldTests() {}

    /** Selects reference blocks at positions contained by the world mask. */
    public static ReferenceWorldTest insideMask(WorldMask worldMask) {
        Objects.requireNonNull(worldMask, "worldMask");
        return (targetBlock, _) -> worldMask.contains(
                targetBlock.globalX(),
                targetBlock.y(),
                targetBlock.globalZ()
        );
    }

    /** Selects surviving reference blocks only over solid generated terrain. */
    public static ReferenceWorldTest survivingOutsideMask(Predicate<Block> survivingBlock) {
        Objects.requireNonNull(survivingBlock, "survivingBlock");
        return (targetBlock, referenceBlock) ->
                survivingBlock.test(referenceBlock) && targetBlock.block().isSolid();
    }
}
