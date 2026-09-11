package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.vector.IntVector;
import com.kntrel.mc.underilla.core.vector.Vector;
import java.util.Objects;

/** A block candidate being processed for one position in a target chunk. */
public class ChunkBlock {

    private final ChunkData targetChunk;
    private final int x;
    private final int y;
    private final int z;
    private final Block initialBlock;
    private final Block trackingBlock = new TrackingBlock();
    private Block block;
    private boolean changed;

    public ChunkBlock(ChunkData targetChunk, int x, int y, int z, Block block) {
        this.targetChunk = Objects.requireNonNull(targetChunk, "targetChunk");
        this.x = x;
        this.y = y;
        this.z = z;
        this.initialBlock = Objects.requireNonNull(block, "block");
        this.block = block;
    }

    public ChunkData targetChunk() {
        return targetChunk;
    }

    public Vector<Integer> position() {
        return new IntVector(x, y, z);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int globalX() {
        return targetChunk.getChunkX() * GenerationConstants.CHUNK_SIZE + x();
    }

    public int globalZ() {
        return targetChunk.getChunkZ() * GenerationConstants.CHUNK_SIZE + z();
    }

    /**
     * Returns the candidate block, including changes made by earlier block patchers.
     * Its mutating operations mark this cursor as changed.
     */
    public Block block() {
        return trackingBlock;
    }

    /** Returns the candidate supplied at the beginning of this block operation chain. */
    public Block initialBlock() {
        return initialBlock;
    }

    public void replace(Block replacement) {
        Block requiredReplacement = Objects.requireNonNull(replacement, "replacement");
        block = requiredReplacement == trackingBlock ? block : requiredReplacement;
        changed = true;
    }

    /** Returns whether a block patcher requested that the candidate be written. */
    public boolean changed() {
        return changed;
    }

    /** Returns the underlying candidate for read-only tests and transformations. */
    public Block candidate() {
        return block;
    }

    /** Creates an isolated, copy-on-write attempt initialized with a proposed block. */
    public ChunkBlock attempt(Block proposedBlock) {
        return new AttemptChunkBlock(this, proposedBlock);
    }

    /** Replaces this candidate with the result of an accepted attempt. */
    public void adopt(ChunkBlock attempt) {
        replace(Objects.requireNonNull(attempt, "attempt").candidate());
    }

    /** Returns the block this operation would replace, including accepted upstream changes. */
    public Block destinationBlock() {
        return targetChunk.getBlock(x, y, z);
    }

    void write() {
        targetChunk.setBlock(x, y, z, block);
    }

    /** Delegates block reads and tracks the compact {@link Block} mutation API. */
    private final class TrackingBlock implements Block {

        @Override
        public Block clone() {
            return block.clone();
        }

        @Override
        public boolean isAir() {
            return block.isAir();
        }

        @Override
        public boolean isSolid() {
            return block.isSolid();
        }

        @Override
        public boolean isLiquid() {
            return block.isLiquid();
        }

        @Override
        public boolean isWaterloggable() {
            return block.isWaterloggable();
        }

        @Override
        public void waterlog() {
            block.waterlog();
            changed = true;
        }

        @Override
        public com.kntrel.mc.underilla.core.api.ID id() {
            return block.id();
        }
    }

    private static final class AttemptChunkBlock extends ChunkBlock {

        private final ChunkBlock parent;
        private final Block trackingBlock = new AttemptTrackingBlock();
        private Block candidate;
        private boolean ownsCandidate;
        private boolean changed;

        private AttemptChunkBlock(ChunkBlock parent, Block candidate) {
            super(parent.targetChunk(), parent.x(), parent.y(), parent.z(),
                    Objects.requireNonNull(candidate, "candidate"));
            this.parent = parent;
            this.candidate = candidate;
        }

        @Override
        public Block block() { return trackingBlock; }

        @Override
        public void replace(Block replacement) {
            Block requiredReplacement = Objects.requireNonNull(replacement, "replacement");
            candidate = requiredReplacement == trackingBlock ? candidate : requiredReplacement;
            ownsCandidate = false;
            changed = true;
        }

        @Override
        public boolean changed() { return changed; }

        @Override
        public Block candidate() { return candidate; }

        @Override
        public Block destinationBlock() { return parent.candidate(); }

        private final class AttemptTrackingBlock implements Block {

            @Override
            public Block clone() { return candidate.clone(); }

            @Override
            public boolean isAir() { return candidate.isAir(); }

            @Override
            public boolean isSolid() { return candidate.isSolid(); }

            @Override
            public boolean isLiquid() { return candidate.isLiquid(); }

            @Override
            public boolean isWaterloggable() { return candidate.isWaterloggable(); }

            @Override
            public void waterlog() {
                if (!ownsCandidate) {
                    candidate = candidate.clone();
                    ownsCandidate = true;
                }
                candidate.waterlog();
                changed = true;
            }

            @Override
            public com.kntrel.mc.underilla.core.api.ID id() { return candidate.id(); }
        }
    }
}
