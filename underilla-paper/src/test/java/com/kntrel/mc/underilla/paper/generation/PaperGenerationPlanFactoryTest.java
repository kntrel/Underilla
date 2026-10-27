package com.kntrel.mc.underilla.paper.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.DualBiomeScope;
import com.kntrel.mc.underilla.core.api.scope.ChunkSnapshot;
import com.kntrel.mc.underilla.core.generation.NoodleCavesPolicy;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaperGenerationPlanFactoryTest {

    private static final ID PLAINS = ID.of("minecraft:plains");
    private static final ID DESERT = ID.of("minecraft:desert");
    private static final Biome PLAINS_BIOME = () -> PLAINS;
    private static final Biome DESERT_BIOME = () -> DESERT;

    @Test
    void placesTheCombinedReferenceTerrainAfterCarversWhenEveryCarvedBiomeIsProtected() {
        NoodleCavesPolicy policy = PaperGenerationPlanFactory.noodleCavesPolicy(
                true,
                Set.of(),
                Set.of(),
                true,
                Set.of(),
                Set.of(),
                Set.of(),
                true);

        assertSame(NoodleCavesPolicy.underground(), policy);
    }

    @Test
    void retainsGranularSurfaceWritesWhenSomeBiomesAreExposedToCarvers() {
        NoodleCavesPolicy.Surface policy = assertInstanceOf(
                NoodleCavesPolicy.Surface.class,
                PaperGenerationPlanFactory.noodleCavesPolicy(
                        true,
                        Set.of(),
                        Set.of(),
                        true,
                        Set.of(DESERT),
                        Set.of(),
                        Set.of(),
                        true));

        assertTrue(policy.predicate().test(snapshot(PLAINS_BIOME)));
        assertFalse(policy.predicate().test(snapshot(DESERT_BIOME)));
        assertTrue(policy.restoreLiquids());
    }

    @Test
    void placesTheCombinedReferenceTerrainAfterCarversWhenCarversAreDisabledForEveryBiome() {
        NoodleCavesPolicy policy = PaperGenerationPlanFactory.noodleCavesPolicy(
                false,
                Set.of(),
                Set.of(),
                false,
                Set.of(),
                Set.of(),
                Set.of(),
                false);

        assertSame(NoodleCavesPolicy.underground(), policy);
    }

    @Test
    void emptyCarverListsExposeEveryBiomeWithoutLoadingTheBiomeRegistry() {
        NoodleCavesPolicy.Surface policy = assertInstanceOf(
                NoodleCavesPolicy.Surface.class,
                PaperGenerationPlanFactory.noodleCavesPolicy(
                        true,
                        Set.of(),
                        Set.of(),
                        false,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        false));

        assertTrue(policy.predicate().test(snapshot(PLAINS_BIOME)));
        assertTrue(policy.predicate().test(snapshot(DESERT_BIOME)));
    }

    @Test
    void exceptOnRemainsACompactComplementSelection() {
        NoodleCavesPolicy.Surface policy = assertInstanceOf(
                NoodleCavesPolicy.Surface.class,
                PaperGenerationPlanFactory.noodleCavesPolicy(
                        true,
                        Set.of(),
                        Set.of(DESERT),
                        false,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        false));

        assertTrue(policy.predicate().test(snapshot(PLAINS_BIOME)));
        assertFalse(policy.predicate().test(snapshot(DESERT_BIOME)));
    }

    private static DualBiomeScope snapshot(Biome biome) {
        return DualBiomeScope.of(ChunkSnapshot.of(0, 0), biome, null);
    }

}
