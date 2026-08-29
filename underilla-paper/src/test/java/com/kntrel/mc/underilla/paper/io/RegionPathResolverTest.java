package com.kntrel.mc.underilla.paper.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegionPathResolverTest {

    @Test
    void explicitRegionPathOverridesWorldAndDimensionConfiguration() {
        Path result = RegionPathResolver.resolve(
                "surfaceWorld", "imports/surface/region", "ignored-world", "not a valid dimension");

        assertEquals(Path.of("imports/surface/region"), result);
    }

    @Test
    void resolvesNamespacedDimensionInsideAWorld() {
        Path result = RegionPathResolver.resolve("surfaceWorld", null, "imports/surface", "example:moon/deep");

        assertEquals(Path.of("imports/surface/dimensions/example/moon/deep/region"), result);
    }

    @Test
    void defaultsAnUnnamespacedDimensionToMinecraft() {
        Path result = RegionPathResolver.resolve("surfaceWorld", null, "world", "overworld");

        assertEquals(Path.of("world/dimensions/minecraft/overworld/region"), result);
    }

    @Test
    void rejectsLegacyWorldNameConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> RegionPathResolver.resolve("surfaceWorld", null, null, null));
    }

    @Test
    void requiresWorldPathAndDimensionTogether() {
        assertThrows(IllegalArgumentException.class,
                () -> RegionPathResolver.resolve("surfaceWorld", null, "world", null));
    }
}
