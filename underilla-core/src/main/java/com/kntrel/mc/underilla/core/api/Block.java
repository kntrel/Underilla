package com.kntrel.mc.underilla.core.api;

public interface Block {

    boolean isAir();
    boolean isSolid();
    boolean isLiquid();
    boolean isWaterloggable();
    void waterlog();
    String getName();
    String getNameSpace();

}
