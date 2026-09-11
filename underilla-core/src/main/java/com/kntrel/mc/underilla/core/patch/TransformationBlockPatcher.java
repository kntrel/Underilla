package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.Block;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** Replaces a block candidate with the candidate returned by a transformation. */
public final class TransformationBlockPatcher implements Patcher<ChunkBlock> {

    private final Function<ChunkBlock, ChunkBlock> transformation;

    public TransformationBlockPatcher(Function<ChunkBlock, ChunkBlock> transformation) {
        this.transformation = Objects.requireNonNull(transformation, "transformation");
    }

    /** Adapts a block transformation while preserving the underlying block's identity. */
    public static TransformationBlockPatcher fromBlocks(UnaryOperator<Block> transformation) {
        Objects.requireNonNull(transformation, "transformation");
        return new TransformationBlockPatcher(block -> {
            Block candidate = block.candidate();
            Block transformed = transformation.apply(candidate);
            if (transformed != null && transformed != candidate) {
                block.replace(transformed);
            }
            return block;
        });
    }

    @Override
    public void patch(ChunkBlock block) {
        Objects.requireNonNull(block, "block");
        ChunkBlock transformed = transformation.apply(block);
        if (transformed == null || transformed == block) {
            return;
        }
        block.replace(transformed.candidate());
    }
}
