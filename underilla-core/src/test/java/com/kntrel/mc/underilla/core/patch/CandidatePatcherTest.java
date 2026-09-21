package com.kntrel.mc.underilla.core.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CandidatePatcherTest {

    @Test
    void commitsAnAcceptedCandidateAfterApplyingAttemptsInOrder() {
        Value original = new Value(1);
        CandidatePatcher<Value> patcher = CandidatePatcher.<Value>take(value -> new Value(value.number))
                .patch(value -> value.number += 2, value -> value.number *= 3)
                .iff((before, taken) -> taken.number > before.number)
                .then((before, taken) -> before.number = taken.number)
                .end();

        patcher.patch(original);

        assertEquals(9, original.number);
    }

    @Test
    void discardsARejectedCandidate() {
        Value original = new Value(1);
        CandidatePatcher<Value> patcher = CandidatePatcher.<Value>take(value -> new Value(value.number))
                .patch(value -> value.number = 10)
                .iff((_, taken) -> taken.number != 10)
                .end();

        patcher.patch(original);

        assertEquals(1, original.number);
    }

    @Test
    void handlesARejectedCandidateWithOtherwise() {
        Value original = new Value(1);
        CandidatePatcher<Value> patcher = CandidatePatcher.<Value>take(value -> new Value(value.number))
                .patch(value -> value.number = 10)
                .iff((_, _) -> false)
                .otherwise((before, taken) -> before.number = -taken.number)
                .end();

        patcher.patch(original);

        assertEquals(-10, original.number);
    }

    @Test
    void takeMustReturnACandidate() {
        Value original = new Value(1);
        CandidatePatcher<Value> patcher = CandidatePatcher.<Value>take(_ -> null)
                .patch(value -> value.number = 10)
                .end();

        assertThrows(NullPointerException.class, () -> patcher.patch(original));
    }

    @Test
    void takeMustReturnADistinctCandidate() {
        Value original = new Value(1);
        CandidatePatcher<Value> patcher = CandidatePatcher.<Value>take(value -> value)
                .end();

        assertThrows(IllegalStateException.class, () -> patcher.patch(original));
    }

    private static final class Value {
        private int number;

        private Value(int number) {
            this.number = number;
        }
    }
}
