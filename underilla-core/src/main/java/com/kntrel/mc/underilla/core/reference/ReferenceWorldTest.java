package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.patch.ChunkBlock;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Tests a target-chunk block against the corresponding reference-world block. */
@FunctionalInterface
public interface ReferenceWorldTest extends BiPredicate<ChunkBlock, Block> {

    @Override
    default ReferenceWorldTest and(BiPredicate<? super ChunkBlock, ? super Block> other) {
        Objects.requireNonNull(other, "other");
        return (targetBlock, referenceBlock) ->
                test(targetBlock, referenceBlock) && other.test(targetBlock, referenceBlock);
    }

    @Override
    default ReferenceWorldTest negate() {
        return (targetBlock, referenceBlock) -> !test(targetBlock, referenceBlock);
    }

    @Override
    default ReferenceWorldTest or(BiPredicate<? super ChunkBlock, ? super Block> other) {
        Objects.requireNonNull(other, "other");
        return (targetBlock, referenceBlock) ->
                test(targetBlock, referenceBlock) || other.test(targetBlock, referenceBlock);
    }
}
