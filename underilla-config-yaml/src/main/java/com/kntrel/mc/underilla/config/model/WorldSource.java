package com.kntrel.mc.underilla.config.model;

import com.kntrel.mc.underilla.core.api.ID;
import java.nio.file.Path;
import java.util.Objects;

/** The mutually exclusive world-path/dimension and region-path source forms. */
public sealed interface WorldSource permits WorldSource.SavedWorld, WorldSource.RegionDirectory {
    /** Optional explicit entity directory; null means resolve it from the saved world, if any. */
    Path entitiesPath();

    Metadata metadata();

    record SavedWorld(Path worldPath, ID dimension, Path entitiesPath, Metadata metadata) implements WorldSource {
        public SavedWorld {
            Objects.requireNonNull(worldPath, "world-path");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(metadata, "metadata");
        }

        public SavedWorld(Path worldPath, ID dimension) {
            this(worldPath, dimension, null, Metadata.inferred());
        }
    }

    record RegionDirectory(Path regionPath, Path entitiesPath, Metadata metadata) implements WorldSource {
        public RegionDirectory {
            Objects.requireNonNull(regionPath, "region-path");
            Objects.requireNonNull(metadata, "metadata");
        }

        public RegionDirectory(Path regionPath) { this(regionPath, null, Metadata.inferred()); }
    }

    /** Optional name/min-height/max-height overrides, flattened into the YAML source mapping.
     * Null components are inferred from the world metadata. maxHeight is exclusive. */
    record Metadata(String name, Integer minHeight, Integer maxHeight) {
        public Metadata {
            if (name != null && name.isBlank()) throw new IllegalArgumentException("Source name must not be blank");
            if (minHeight != null && maxHeight != null && maxHeight <= minHeight) {
                throw new IllegalArgumentException("Source max-height must exceed min-height");
            }
        }

        public static Metadata inferred() { return new Metadata(null, null, null); }
    }
}
