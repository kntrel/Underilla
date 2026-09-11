package com.kntrel.mc.underilla.core.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PatcherTest {

    @Test
    void startsACandidatePatcher() {
        Value value = new Value(1);
        Patcher<Value> patcher = Patcher.<Value>take(original -> new Value(original.number))
                .patch(candidate -> candidate.number++)
                .then((original, candidate) -> original.number = candidate.number)
                .end();

        patcher.patch(value);

        assertEquals(2, value.number);
    }

    @Test
    void startsAPredicatePatcher() {
        Value value = new Value(1);
        Patcher<Value> patcher = Patcher.<Value>iff(subject -> subject.number > 0)
                .then(subject -> subject.number = 2)
                .otherwise(subject -> subject.number = 3)
                .end();

        patcher.patch(value);

        assertEquals(2, value.number);
    }

    @Test
    void startsAPatcherPipeline() {
        Value value = new Value(1);
        Patcher<Value> patcher = Patcher.sequence(
                subject -> subject.number += 2,
                subject -> subject.number *= 3
        );

        patcher.patch(value);

        assertEquals(9, value.number);
    }

    private static final class Value {
        private int number;

        private Value(int number) {
            this.number = number;
        }
    }
}
