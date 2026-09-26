package com.kntrel.mc.underilla.core.generation;

/** A generation callback phase in Underilla's world-generation plan. */
public enum Phase {
    NOISE,
    SURFACE,
    CARVERS,
    FEATURES,
    LOAD;

    /** The stable order in which phases occur during generation. */
    public int order() {
        return ordinal();
    }
}
