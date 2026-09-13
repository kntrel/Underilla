package com.kntrel.mc.underilla.paper.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.generation.NoodleCavesPolicy;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaperGenerationPlanFactoryTest {

    private static final ID PLAINS = ID.of("minecraft:plains");
    private static final ID DESERT = ID.of("minecraft:desert");

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

        assertTrue(policy.predicate().test(new TestBiome(PLAINS)));
        assertFalse(policy.predicate().test(new TestBiome(DESERT)));
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

        assertTrue(policy.predicate().test(new TestBiome(PLAINS)));
        assertTrue(policy.predicate().test(new TestBiome(DESERT)));
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

        assertTrue(policy.predicate().test(new TestBiome(PLAINS)));
        assertFalse(policy.predicate().test(new TestBiome(DESERT)));
    }
}
