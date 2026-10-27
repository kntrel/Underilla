package com.kntrel.mc.underilla.config.model;

import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.config.context.BlockContext;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record WorldsConfig(Target target, Source source, Underground underground) {
    public WorldsConfig {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(underground, "underground");
    }

    public static WorldsConfig defaults() {
        return new WorldsConfig(new Target("world", "VANILLA"),
                new Source(new WorldSource.SavedWorld(Path.of("world_surface"), ID.of("overworld")), List.of()),
                new Underground(UndergroundType.VANILLA,
                        new WorldSource.SavedWorld(Path.of("world_caves"), ID.of("overworld"))));
    }

    /** outsideGenerator accepts VANILLA or an installed generator plugin's name. */
    public record Target(String name, String outsideGenerator) {
        public Target {
            if (Objects.requireNonNull(name, "name").isBlank()) throw new IllegalArgumentException("Target name must not be blank");
            if (Objects.requireNonNull(outsideGenerator, "outside-generator").isBlank()) {
                throw new IllegalArgumentException("outside-generator must not be blank");
            }
        }
    }

    /** Replacement conditions have source block/biome/world data only. Rules retain first-match order. */
    public record Source(WorldSource source, List<ReplacementRule<BlockContext>> replaceBlocks) {
        public Source {
            Objects.requireNonNull(source, "source");
            replaceBlocks = List.copyOf(replaceBlocks);
        }
    }

    /** source is required for WORLD and may be omitted for VANILLA. */
    public record Underground(UndergroundType type, WorldSource source) {
        public Underground {
            Objects.requireNonNull(type, "type");
            if (type == UndergroundType.WORLD) Objects.requireNonNull(source, "underground.source");
        }
    }

    public enum UndergroundType { VANILLA, WORLD }
}
