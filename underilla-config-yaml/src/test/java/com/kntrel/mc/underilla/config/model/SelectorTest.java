package com.kntrel.mc.underilla.config.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SelectorTest {
    @Test
    void isUsableDirectlyAsAPredicate() {
        Selector<Integer> selector = value -> value > 1;
        Predicate<Integer> predicate = selector;
        assertTrue(predicate.test(2));
        assertFalse(predicate.test(1));
        assertTrue(Selector.all().test(null));
        assertFalse(Selector.none().test(null));
    }

    @Test
    void listsUseShortCircuitOrAndSnapshotTheirInput() {
        var input = new ArrayList<Predicate<? super Integer>>(List.of(value -> value < 0, value -> value >= 10));
        Selector<Integer> selector = Selector.anyOf(input);
        input.clear();
        assertTrue(selector.test(-1));
        assertFalse(selector.test(5));
        assertTrue(selector.test(10));
        assertFalse(Selector.anyOf(List.of()).test(null));
        assertTrue(Selector.<Integer>anyOf(List.of(Selector.all(), _ -> {
            throw new AssertionError("Later predicate evaluated");
        })).test(0));
    }

    @Test
    void compositionRetainsSelectorType() {
        Selector<Integer> positive = value -> value > 0;
        Selector<Integer> smallPositive = positive.and(value -> value < 10);
        Selector<Integer> outside = smallPositive.negate();
        Selector<Integer> positiveOrNegativeOne = positive.or(value -> value == -1);
        assertTrue(smallPositive.test(5));
        assertFalse(smallPositive.test(11));
        assertTrue(outside.test(11));
        assertTrue(positiveOrNegativeOne.test(-1));
        assertFalse(positiveOrNegativeOne.test(-2));
    }
}
