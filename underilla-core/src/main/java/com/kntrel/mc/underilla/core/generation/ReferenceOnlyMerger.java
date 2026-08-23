package com.kntrel.mc.underilla.core.generation;

/** Copies the reference world without retaining generated underground terrain. */
public final class ReferenceOnlyMerger extends AbstractMerger {

    private final int boundaryY;

    public ReferenceOnlyMerger(GenerationContext context) {
        super(context);
        this.boundaryY = context.config().generationAreaMinY();
    }

    @Override
    protected int mergeBoundaryY(int globalX, int globalZ) { return boundaryY; }

    @Override
    public boolean shouldGenerateNoise() { return false; }
}
