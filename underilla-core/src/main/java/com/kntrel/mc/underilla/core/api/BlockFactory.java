package com.kntrel.mc.underilla.core.api;

/** Creates platform-specific block values for the platform-neutral generation code. */
public interface BlockFactory {

    Block air();

    Block create(String name);
}
