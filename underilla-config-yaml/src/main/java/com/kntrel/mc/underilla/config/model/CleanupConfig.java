package com.kntrel.mc.underilla.config.model;

import com.kntrel.mc.underilla.config.context.BlockContext;
import com.kntrel.mc.underilla.config.context.EntityContext;
import java.util.List;
import java.util.Objects;

/** Cleanup conditions use target data only. Replacement lists retain their first-match order. */
public record CleanupConfig(Blocks blocks, IllegalBlocks illegalBlocks, Entities entities) {
    public CleanupConfig {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(illegalBlocks, "illegal-blocks");
        Objects.requireNonNull(entities, "entities");
    }

    public record Blocks(boolean enabled, List<ReplacementRule<BlockContext>> supportBlocks,
                         List<ReplacementRule<BlockContext>> replaceBlocks) {
        public Blocks {
            supportBlocks = List.copyOf(supportBlocks);
            replaceBlocks = List.copyOf(replaceBlocks);
        }
    }

    /** Independent of blocks.enabled; unmatched invalid blocks become air. */
    public record IllegalBlocks(boolean enabled, List<ReplacementRule<BlockContext>> replaceBlocks) {
        public IllegalBlocks { replaceBlocks = List.copyOf(replaceBlocks); }
    }

    /** Local entity/world/chunk data from the target; biome and block data are unavailable. */
    public record Entities(boolean enabled, Selector<EntityContext> removeIf) {
        public Entities { Objects.requireNonNull(removeIf, "remove-if"); }
    }
}
