package com.kntrel.mc.underilla.core.api;

/** Read-only identity, seed, dimension, and vertical bounds of a world. */
public interface World {

    static World of(String name, ID dimension, int minimumY, int maximumY) {
        return of(name, dimension, minimumY, maximumY, 0L);
    }

    static World of(String name, ID dimension, int minimumY, int maximumY, long seed) {
        return new WorldImpl(name, dimension, minimumY, maximumY, seed);
    }

    String name();

    /** {@return the dimension resource ID, such as minecraft:overworld} */
    ID dimension();

    /** {@return the world seed, or 0 when saved metadata is unavailable} */
    long seed();

    int minHeight();

    int maxHeight();
}
