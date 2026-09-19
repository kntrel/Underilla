package com.kntrel.mc.underilla.core.api;

public interface Block extends Cloneable {

    Block clone();
    boolean isAir();
    boolean isSolid();
    boolean isLiquid();
    boolean isWaterloggable();
    boolean isLegal();
    void waterlog();
    ID id();

}
