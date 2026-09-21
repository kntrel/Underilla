package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.BiomeData;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.simulation.biome.BiomeProvider;
import java.util.Objects;

/** Applies the inspector's configured biome patch to the simulation's base biome source. */
final class InspectorBiomeProvider implements BiomeProvider {

    private final Patcher<BiomeData> biomePatcher;
    private final BiomeProvider vanillaProvider;

    InspectorBiomeProvider(Patcher<BiomeData> biomePatcher, BiomeProvider vanillaProvider) {
        this.biomePatcher = Objects.requireNonNull(biomePatcher, "biomePatcher");
        this.vanillaProvider = Objects.requireNonNull(vanillaProvider, "vanillaProvider");
    }

    @Override
    public Biome getAt(int x, int y, int z) {
        MutableBiomeData biome = new MutableBiomeData(vanillaProvider.getAt(x, y, z), x, y, z);
        biomePatcher.patch(biome);
        return biome.get();
    }

    private static final class MutableBiomeData implements BiomeData {

        private Biome biome;
        private final int x;
        private final int y;
        private final int z;

        private MutableBiomeData(Biome biome, int x, int y, int z) {
            this.biome = Objects.requireNonNull(biome, "biome");
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Biome get() { return biome; }

        @Override
        public void set(Biome biome) { this.biome = Objects.requireNonNull(biome, "biome"); }

        @Override
        public int getX() { return x; }

        @Override
        public int getY() { return y; }

        @Override
        public int getZ() { return z; }
    }
}
