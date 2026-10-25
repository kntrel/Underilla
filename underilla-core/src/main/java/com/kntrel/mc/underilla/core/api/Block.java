package com.kntrel.mc.underilla.core.api;

public interface Block extends Cloneable {

    /** Global block coordinates. Factory-created values start at the origin. */
    int x();
    int y();
    int z();

    Block clone();
    boolean isAir();
    boolean isSolid();
    boolean isLiquid();
    boolean isWaterloggable();
    boolean isLegal();
    void waterlog();
    ID id();

}
