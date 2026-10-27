package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.World;
import com.kntrel.mc.underilla.core.api.WorldInfo;
import com.kntrel.mc.underilla.core.api.ID;

public class BukkitWorldInfo implements WorldInfo, World {

    // FIELDS
    private final org.bukkit.generator.WorldInfo worldInfo_;


    // CONSTRUCTORS
    public BukkitWorldInfo(org.bukkit.generator.WorldInfo base) { this.worldInfo_ = base; }

    @Override
    public String name() { return worldInfo_.getName(); }

    @Override
    public ID dimension() {
        return ID.of(worldInfo_.getKey().toString());
    }


    @Override
    public int minHeight() { return worldInfo_.getMinHeight(); }

    @Override
    public int maxHeight() { return worldInfo_.getMaxHeight(); }

    @Override
    public long seed() { return worldInfo_.getSeed(); }

    @Override
    public long getSeed() { return this.worldInfo_.getSeed(); }

    @Override
    public int getMaxHeight() { return this.worldInfo_.getMaxHeight(); }

    @Override
    public int getMinHeight() { return this.worldInfo_.getMinHeight(); }
}
