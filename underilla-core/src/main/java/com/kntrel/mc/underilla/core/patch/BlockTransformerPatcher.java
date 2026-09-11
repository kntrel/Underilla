package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.Block;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Transforms the current block candidate without traversing the chunk itself. */
public final class BlockTransformerPatcher implements Patcher<ChunkBlock> {

    private final UnaryOperator<Block> transformer;

    public BlockTransformerPatcher(UnaryOperator<Block> transformer) {
        this.transformer = Objects.requireNonNull(transformer, "transformer");
    }

    @Override
    public void patch(ChunkBlock block) {
        Objects.requireNonNull(block, "block");
        Block candidate = block.block();
        Block replacement = transformer.apply(candidate);
        if (replacement != candidate) {
            block.replace(replacement);
        }
    }
}
