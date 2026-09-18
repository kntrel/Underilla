package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.patch.ChunkBlock;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;
import java.util.function.Supplier;

/** Replaces a target candidate with the corresponding block from a reference world. */
public final class ReferenceWorldBlockPatcher implements Patcher<ChunkBlock> {

    private final WorldReader referenceWorld;
    private final Supplier<Block> air;

    public ReferenceWorldBlockPatcher(
            WorldReader referenceWorld,
            Supplier<Block> air
    ) {
        this.referenceWorld = Objects.requireNonNull(referenceWorld, "referenceWorld");
        this.air = Objects.requireNonNull(air, "air");
    }

    @Override
    public void patch(ChunkBlock targetBlock) {
        Objects.requireNonNull(targetBlock, "targetBlock");
        Block referenceBlock = referenceWorld
                .blockAt(targetBlock.globalX(), targetBlock.y(), targetBlock.globalZ())
                .orElseGet(air);
        targetBlock.replace(referenceBlock);
    }
}
