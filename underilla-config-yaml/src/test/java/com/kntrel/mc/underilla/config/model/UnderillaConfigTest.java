package com.kntrel.mc.underilla.config.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kntrel.mc.underilla.config.context.BlockContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnderillaConfigTest {


    @Test
    void supportsAutoBoundsAndValidatesOnlyKnownHorizontalRanges() {
        assertNotNull(new AreaConfig(HorizontalBound.auto(), HorizontalBound.fixed(-32),
                HorizontalBound.fixed(0), HorizontalBound.auto(), -64, 320));
        assertThrows(IllegalArgumentException.class, () -> HorizontalBound.fixed(-1));
        assertThrows(IllegalArgumentException.class, () -> new AreaConfig(HorizontalBound.fixed(16),
                HorizontalBound.fixed(0), HorizontalBound.fixed(0), HorizontalBound.fixed(16), -64, 320));
        assertThrows(IllegalArgumentException.class, () -> new AreaConfig(HorizontalBound.auto(),
                HorizontalBound.auto(), HorizontalBound.auto(), HorizontalBound.auto(), 10, 10));
    }

    @Test
    void sourceFormsSupportPartialMetadataOverridesAndRequireWorldSourcesForCopying() {
        var overrides = new WorldSource.Metadata("Reference", -64, null);
        var direct = new WorldSource.RegionDirectory(Path.of("legacy/region"), Path.of("legacy/entities"), overrides);
        var copied = new WorldsConfig.Underground(WorldsConfig.UndergroundType.WORLD, direct);
        assertEquals(overrides, copied.source().metadata());
        assertThrows(NullPointerException.class, () -> new WorldsConfig.Underground(WorldsConfig.UndergroundType.WORLD, null));
        assertNotNull(new WorldsConfig.Underground(WorldsConfig.UndergroundType.VANILLA, null));
        assertThrows(IllegalArgumentException.class, () -> new WorldSource.Metadata(null, 0, -64));
    }

    @Test
    void copiesReplacementListsWithoutChangingFirstMatchOrder() {
        var first = ReplacementRule.<BlockContext>of(Selector.all(), "stone");
        var second = ReplacementRule.<BlockContext>of(Selector.all(), "dirt");
        var mutable = new ArrayList<>(List.of(first, second));
        var cleanup = new CleanupConfig.Blocks(true, mutable, mutable);
        var illegal = new CleanupConfig.IllegalBlocks(true, mutable);
        var source = new WorldsConfig.Source(new WorldSource.RegionDirectory(Path.of("region")), mutable);
        mutable.clear();
        assertEquals(List.of(first, second), cleanup.supportBlocks());
        assertEquals(List.of(first, second), cleanup.replaceBlocks());
        assertEquals(List.of(first, second), illegal.replaceBlocks());
        assertEquals(List.of(first, second), source.replaceBlocks());
        assertThrows(UnsupportedOperationException.class, () -> source.replaceBlocks().clear());
    }

    @Test
    void rejectsInvalidAdaptiveDepthsAndCacheSizes() {
        assertThrows(IllegalArgumentException.class, () -> new MergeConfig.AdaptiveDepth(true, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new MergeConfig.Surface(1,
                new MergeConfig.AdaptiveDepth(true, 10, 2), Selector.none()));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceConfig.Cache(0, 64));
    }
}
