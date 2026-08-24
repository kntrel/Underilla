package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.reader.WorldReader;

/** Construction compatibility used only by the unchanged merger characterization fixture. */
final class SurfaceMerger {

    private final GenerationContext context;

    SurfaceMerger(WorldReader surfaceWorld, GenerationContext context) {
        this.context = context;
    }

    GenerationContext context() {
        return context;
    }
}
