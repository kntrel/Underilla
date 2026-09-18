package com.kntrel.mc.underilla.core.patch;

import com.kntrel.mc.underilla.core.api.Block;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Routes a possibly speculative block candidate to one of two downstream patchers. */
public final class RoutingBlockPatcher implements Patcher<ChunkBlock> {

    public static final Patcher<ChunkBlock> NOOP = _ -> {},
                                            ROLLBACK = _ -> {};

    private final Patcher<ChunkBlock> upstream;
    private final BiPredicate<ChunkBlock, Block> test;
    private final Patcher<ChunkBlock> ifTrue;
    private final Patcher<ChunkBlock> ifFalse;

    private RoutingBlockPatcher(
            Patcher<ChunkBlock> upstream,
            BiPredicate<ChunkBlock, Block> test,
            Patcher<ChunkBlock> ifTrue,
            Patcher<ChunkBlock> ifFalse
    ) {
        this.upstream = upstream;
        this.test = Objects.requireNonNull(test, "test");
        this.ifTrue = Objects.requireNonNull(ifTrue, "ifTrue");
        this.ifFalse = Objects.requireNonNull(ifFalse, "ifFalse");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void patch(ChunkBlock block) {
        Objects.requireNonNull(block, "block");
        ChunkBlock subject = upstream == null ? block : block.attempt(block.candidate());

        if (upstream != null) {
            upstream.patch(subject);
        }

        Patcher<ChunkBlock> downstream = test.test(block, subject.candidate()) ? ifTrue : ifFalse;
        if (downstream == ROLLBACK) { return; }
        if (subject != block && subject.changed()) { block.adopt(subject); }
        downstream.patch(block);
    }

    public static final class Builder {

        private final List<Patcher<ChunkBlock>> upstream = new ArrayList<>();
        private BiPredicate<ChunkBlock, Block> test = (_, _) -> true;
        private Patcher<ChunkBlock> ifTrue = NOOP;
        private Patcher<ChunkBlock> ifFalse = ROLLBACK;

        private Builder() {}

        @SafeVarargs
        public final Builder upstream(Patcher<ChunkBlock>... patchers) {
            Objects.requireNonNull(patchers, "patchers");
            for (Patcher<ChunkBlock> patcher : patchers) {
                upstream.add(Objects.requireNonNull(patcher, "patcher"));
            }
            return this;
        }

        public Builder test(BiPredicate<ChunkBlock, Block> test) {
            this.test = Objects.requireNonNull(test, "test");
            return this;
        }

        public Builder ifTrue(Patcher<ChunkBlock> patcher) {
            this.ifTrue = Objects.requireNonNull(patcher, "patcher");
            return this;
        }

        public Builder ifFalse(Patcher<ChunkBlock> patcher) {
            this.ifFalse = Objects.requireNonNull(patcher, "patcher");
            return this;
        }

        public RoutingBlockPatcher build() {
            List<Patcher<ChunkBlock>> upstreamSnapshot = List.copyOf(upstream);
            Patcher<ChunkBlock> composedUpstream = upstreamSnapshot.isEmpty()
                    ? null
                    : block -> upstreamSnapshot.forEach(patcher -> patcher.patch(block));
            return new RoutingBlockPatcher(composedUpstream, test, ifTrue, ifFalse);
        }
    }

}
