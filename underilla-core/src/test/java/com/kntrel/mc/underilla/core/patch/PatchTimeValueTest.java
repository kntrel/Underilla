package com.kntrel.mc.underilla.core.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PatchTimeValueTest {

    @Test
    void calculatesOneValueForOrdinaryDownstreamPatchers() {
        AtomicInteger calculations = new AtomicInteger();
        AtomicInteger result = new AtomicInteger();
        PatchTimeValue<Integer, Integer> doubled = Patcher.value(subject -> {
            calculations.incrementAndGet();
            return subject * 2;
        });
        Patcher<Integer> patcher = Patcher.with(doubled).sequence(
                _ -> result.addAndGet(doubled.get()),
                _ -> result.addAndGet(doubled.get())
        );

        patcher.patch(3);

        assertEquals(1, calculations.get());
        assertEquals(12, result.get());
        assertThrows(NoSuchElementException.class, doubled::get);
    }

    @Test
    void bindsMultipleValuesInDeclarationOrder() {
        AtomicInteger result = new AtomicInteger();
        PatchTimeValue<Integer, Integer> firstValue = PatchTimeValue.from(subject -> subject + 1);
        PatchTimeValue<Integer, Integer> secondValue = PatchTimeValue.from(
                subject -> subject + firstValue.get());
        Patcher<Integer> patcher = PatcherPipeline.with(firstValue, secondValue).sequence(
                subject -> result.set(firstValue.get() * secondValue.get()));

        patcher.patch(2);

        assertEquals(15, result.get());
        assertThrows(NoSuchElementException.class, firstValue::get);
        assertThrows(NoSuchElementException.class, secondValue::get);
    }

    @Test
    void restoresTheOuterValueAfterNestedPatching() {
        AtomicInteger result = new AtomicInteger();
        PatchTimeValue<Integer, Integer> value = PatchTimeValue.from(subject -> subject);
        Patcher<Integer> inner = PatcherPipeline.with(value).sequence(
                _ -> result.addAndGet(value.get()));
        Patcher<Integer> outer = PatcherPipeline.with(value).sequence(_ -> {
            result.addAndGet(value.get());
            inner.patch(3);
            result.addAndGet(value.get());
        });

        outer.patch(2);

        assertEquals(7, result.get());
        assertThrows(NoSuchElementException.class, value::get);
    }

    @Test
    void clearsTheValueWhenADownstreamPatcherFails() {
        PatchTimeValue<Integer, Integer> patchTimeValue = PatchTimeValue.from(subject -> subject);
        Patcher<Integer> patcher = PatcherPipeline.with(patchTimeValue).sequence(
                _ -> { throw new IllegalStateException("failed"); });

        assertThrows(IllegalStateException.class, () -> patcher.patch(2));
        assertThrows(NoSuchElementException.class, patchTimeValue::get);
    }

    @Test
    void isolatesConcurrentInvocations() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        Map<Integer, Integer> observed = new ConcurrentHashMap<>();
        PatchTimeValue<Integer, Integer> patchTimeValue = PatchTimeValue.from(subject -> subject * 10);
        Patcher<Integer> patcher = PatcherPipeline.with(patchTimeValue).sequence(subject -> {
            entered.countDown();
            try {
                if (!entered.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent invocation did not enter the scope");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            observed.put(subject, patchTimeValue.get());
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> patcher.patch(1));
            var second = executor.submit(() -> patcher.patch(2));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(Map.of(1, 10, 2, 20), observed);
    }
}
