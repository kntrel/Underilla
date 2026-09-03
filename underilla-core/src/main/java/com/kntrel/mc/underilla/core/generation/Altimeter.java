package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.HeightMapType;
import com.kntrel.mc.underilla.core.api.WorldInfo;

/** Resolves a base height for the platform's requested height-map semantics. */
@FunctionalInterface
public interface Altimeter {

    int baseHeight(WorldInfo worldInfo, int x, int z, HeightMapType heightMap);
}
