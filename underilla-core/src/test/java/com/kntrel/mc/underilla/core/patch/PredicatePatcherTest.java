package com.kntrel.mc.underilla.core.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PredicatePatcherTest {

    @Test
    void patchesThenWhenThePredicateMatches() {
        Value value = new Value(1);
        PredicatePatcher<Value> patcher = PredicatePatcher.<Value>iff(subject -> subject.number > 0)
                .then(subject -> subject.number = 2)
                .otherwise(subject -> subject.number = 3)
                .end();

        patcher.patch(value);

        assertEquals(2, value.number);
    }

    @Test
    void patchesOtherwiseWhenThePredicateDoesNotMatch() {
        Value value = new Value(-1);
        PredicatePatcher<Value> patcher = PredicatePatcher.<Value>iff(subject -> subject.number > 0)
                .then(subject -> subject.number = 2)
                .otherwise(subject -> subject.number = 3)
                .end();

        patcher.patch(value);

        assertEquals(3, value.number);
    }

    private static final class Value {
        private int number;

        private Value(int number) {
            this.number = number;
        }
    }
}
