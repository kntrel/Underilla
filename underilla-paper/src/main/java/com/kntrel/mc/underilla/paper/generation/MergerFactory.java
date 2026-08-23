package com.kntrel.mc.underilla.paper.generation;

import com.kntrel.mc.underilla.core.generation.AbsoluteMerger;
import com.kntrel.mc.underilla.core.generation.GenerationContext;
import com.kntrel.mc.underilla.core.generation.Merger;
import com.kntrel.mc.underilla.core.generation.ReferenceOnlyMerger;
import com.kntrel.mc.underilla.core.generation.SurfaceMerger;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Locale;

/** Maps the user-facing merge mode to a concrete core strategy at the application boundary. */
public final class MergerFactory {

    private MergerFactory() {}

    public static Merger create(String configuredMode, WorldReader surfaceWorld, GenerationContext context) {
        if (configuredMode == null) {
            throw new IllegalArgumentException("Merge mode must be configured");
        }

        return switch (configuredMode.trim().toUpperCase(Locale.ROOT)) {
            case "ABSOLUTE" -> new AbsoluteMerger(context);
            case "SURFACE" -> new SurfaceMerger(surfaceWorld, context);
            case "NONE" -> new ReferenceOnlyMerger(context);
            default -> throw new IllegalArgumentException("Unknown merge mode: " + configuredMode);
        };
    }
}
