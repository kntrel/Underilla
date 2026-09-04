package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Biome;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Describes whether vanilla noodle caves may remain visible in the reference terrain.
 *
 * <p>This is deliberately expressed in terms of the world a user wants to generate. It does not
 * expose the generation phases used to implement that result.</p>
 */
public sealed interface NoodleCavesPolicy permits NoodleCavesPolicy.Underground, NoodleCavesPolicy.Surface {

    /** Keeps noodle caves below the copied reference terrain. */
    final class Underground implements NoodleCavesPolicy {

        private static final Underground INSTANCE = new Underground();

        private Underground() {}
    }

    /**
     * Allows noodle caves to remain visible through reference terrain where {@code predicate} matches.
     *
     * @param predicate determines the biomes in which noodle caves may cut through terrain
     * @param restoreLiquids whether copied liquids remain after noodle carving
     */
    record Surface(Predicate<Biome> predicate, boolean restoreLiquids) implements NoodleCavesPolicy {

        public Surface {
            Objects.requireNonNull(predicate, "predicate");
        }
    }

    static Underground underground() {
        return Underground.INSTANCE;
    }

    static Surface surface(Predicate<Biome> predicate, boolean restoreLiquids) {
        return new Surface(predicate, restoreLiquids);
    }
}
