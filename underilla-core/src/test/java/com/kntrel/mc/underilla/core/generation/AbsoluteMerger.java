package com.kntrel.mc.underilla.core.generation;

/** Construction compatibility used only by the unchanged merger characterization fixture. */
final class AbsoluteMerger {

    private final GenerationContext context;

    AbsoluteMerger(GenerationContext context) {
        this.context = context;
    }

    GenerationContext context() {
        return context;
    }
}
