package com.kntrel.mc.underilla.core.api;

/** Read-only identity, dimension, and vertical bounds of a world. */
public interface World {

    static World of(String name, ID dimension, int minimumY, int maximumY) {
        return new WorldImpl(name, dimension, minimumY, maximumY);
    }

    String name();

    /** {@return the dimension resource ID, such as minecraft:overworld} */
    ID dimension();

    int minHeight();

    int maxHeight();
}
