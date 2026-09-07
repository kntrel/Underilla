package com.kntrel.mc.underilla.core.api;

/** A mutable entity currently present in a target chunk. */
public interface Entity {

    String getType();

    void remove();
}
