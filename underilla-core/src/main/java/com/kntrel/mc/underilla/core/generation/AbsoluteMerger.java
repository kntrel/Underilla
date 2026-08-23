package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.reader.ChunkReader;

/** Merges the reference world above one fixed Y boundary. */
public final class AbsoluteMerger extends AbstractMerger {

    private final int boundaryY;

    public AbsoluteMerger(GenerationContext context) {
        super(context);
        this.boundaryY = Math.max(context.config().generationAreaMinY(),
                Math.min(context.config().maxHeightOfCaves(), context.config().generationAreaMaxY()));
    }

    @Override
    protected int mergeBoundaryY(int globalX, int globalZ) { return boundaryY; }
}
