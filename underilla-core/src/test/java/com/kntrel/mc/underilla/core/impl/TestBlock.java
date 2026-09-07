package com.kntrel.mc.underilla.core.impl;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ID;
import java.util.Objects;

/** In-memory block implementation for generation tests. */
public final class TestBlock implements Block {

    private final ID id;
    private final boolean solid;
    private final boolean liquid;
    private final boolean air;
    private final boolean waterloggable;
    private boolean waterlogged;

    private TestBlock(String name, boolean solid, boolean liquid, boolean air, boolean waterloggable) {
        this.id = ID.of(Objects.requireNonNull(name, "name"));
        this.solid = solid;
        this.liquid = liquid;
        this.air = air;
        this.waterloggable = waterloggable;
    }

    public static TestBlock air(String name) {
        return new TestBlock(name, false, false, true, false);
    }

    public static TestBlock solid(String name) {
        return new TestBlock(name, true, false, false, false);
    }

    public static TestBlock nonSolid(String name) {
        return new TestBlock(name, false, false, false, false);
    }

    public static TestBlock liquid(String name) {
        return new TestBlock(name, false, true, false, false);
    }

    public static TestBlock waterloggable(String name, boolean solid) {
        return new TestBlock(name, solid, false, false, true);
    }

    @Override
    public boolean isAir() { return air; }

    @Override
    public boolean isSolid() { return solid; }

    @Override
    public boolean isLiquid() { return liquid; }

    @Override
    public boolean isWaterloggable() { return waterloggable; }

    @Override
    public void waterlog() {
        if (waterloggable) {
            waterlogged = true;
        }
    }

    public boolean isWaterlogged() { return waterlogged; }

    @Override
    public ID id() { return id; }

    @Override
    public String toString() { return id.toString(); }
}
