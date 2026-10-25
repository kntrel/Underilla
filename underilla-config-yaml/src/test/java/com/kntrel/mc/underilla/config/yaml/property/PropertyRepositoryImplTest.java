package com.kntrel.mc.underilla.config.yaml.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.kntrel.mc.underilla.config.property.Property;
import com.kntrel.mc.underilla.config.property.PropertyImpl;
import com.kntrel.mc.underilla.config.property.PropertyRepository;
import org.junit.jupiter.api.Test;

class PropertyRepositoryImplTest {

    private final Property<String, Integer> length = new PropertyImpl<>(
            "length", String.class, Integer.class, String::length);
    private final Property<String, String> uppercase = new PropertyImpl<>(
            "uppercase", String.class, String.class, String::toUpperCase);
    private final Property<String, Integer> hash = new PropertyImpl<>(
            "hash", String.class, Integer.class, String::hashCode);
    private final Property<CharSequence, Integer> sequenceLength = new PropertyImpl<>(
            "length", CharSequence.class, Integer.class, CharSequence::length);

    @Test
    void looksUpByObjectTypeNameAndValueType() {
        PropertyRepository repository = PropertyRepository.from(
                List.of(length, sequenceLength, uppercase, hash));

        assertEquals(List.of(length, uppercase, hash), repository.getAll(String.class));
        assertEquals(List.of(sequenceLength), repository.getAll(CharSequence.class));
        assertSame(length, repository.get(String.class, "length").orElseThrow());
        assertSame(sequenceLength, repository.get(CharSequence.class, "length").orElseThrow());
        assertEquals(3, repository.get(String.class, "length", Integer.class)
                .orElseThrow().get("abc"));
        assertEquals("ABC", repository.get(String.class, "uppercase", String.class)
                .orElseThrow().get("abc"));
        assertEquals(List.of(length, hash), repository.get(String.class, Integer.class));
    }

    @Test
    void returnsEmptyResultsForMissingOrNonExactTypes() {
        PropertyRepository repository = PropertyRepository.from(List.of(length, sequenceLength));

        assertTrue(repository.get(String.class, "missing").isEmpty());
        assertTrue(repository.get(Integer.class, "length").isEmpty());
        assertTrue(repository.getAll(Integer.class).isEmpty());
        assertTrue(repository.get(String.class, "length", String.class).isEmpty());
        assertTrue(repository.get(String.class, "length", Number.class).isEmpty());
        assertTrue(repository.get(String.class, Number.class).isEmpty());
        assertTrue(repository.get(StringBuilder.class, "length").isEmpty());
        assertTrue(repository.getAll(Object.class).isEmpty());
        assertTrue(PropertyRepository.from(List.of()).getAll(String.class).isEmpty());
    }

    @Test
    void snapshotsInputAndReturnsUnmodifiableLists() {
        List<Property<?, ?>> input = new ArrayList<>(List.of(length, uppercase, hash));
        PropertyRepository repository = PropertyRepository.from(input);

        input.clear();
        input.add(sequenceLength);

        assertEquals(List.of(length, uppercase, hash), repository.getAll(String.class));
        assertTrue(repository.getAll(CharSequence.class).isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> repository.getAll(String.class).clear());
        assertThrows(UnsupportedOperationException.class,
                () -> repository.get(String.class, Integer.class).clear());
        assertSame(length, repository.get(String.class, "length").orElseThrow());
    }

    @Test
    void rejectsDuplicateNamesWithinAnObjectType() {
        Property<String, String> duplicate = new PropertyImpl<>(
                "length", String.class, String.class, value -> value);

        assertThrows(IllegalArgumentException.class,
                () -> PropertyRepository.from(List.of(length, duplicate)));
        assertThrows(IllegalArgumentException.class,
                () -> PropertyRepository.from(List.of(length, length)));
    }

    @Test
    void rejectsNullInputAndMetadata() {
        assertThrows(NullPointerException.class, () -> PropertyRepository.from(null));
        assertThrows(NullPointerException.class,
                () -> PropertyRepository.from(Arrays.asList(length, null)));
        assertThrows(NullPointerException.class, () -> PropertyRepository.from(List.of(
                new PropertyImpl<>(null, String.class, Integer.class, String::length))));
        assertThrows(NullPointerException.class, () -> PropertyRepository.from(List.of(
                new PropertyImpl<>("length", null, Integer.class, String::length))));
        assertThrows(NullPointerException.class, () -> PropertyRepository.from(List.of(
                new PropertyImpl<>("length", String.class, null, String::length))));
    }

    @Test
    void rejectsNullLookupArguments() {
        PropertyRepository repository = PropertyRepository.from(List.of());

        assertThrows(NullPointerException.class, () -> repository.getAll(null));
        assertThrows(NullPointerException.class, () -> repository.get(null, "length"));
        assertThrows(NullPointerException.class, () -> repository.get(String.class, (String) null));
        assertThrows(NullPointerException.class,
                () -> repository.get(null, "length", Integer.class));
        assertThrows(NullPointerException.class,
                () -> repository.get(String.class, null, Integer.class));
        assertThrows(NullPointerException.class,
                () -> repository.get(String.class, "length", null));
        assertThrows(NullPointerException.class, () -> repository.get(null, Integer.class));
        assertThrows(NullPointerException.class,
                () -> repository.get(String.class, (Class<Integer>) null));
    }

    @Test
    void supportsConcurrentLookups() throws Exception {
        PropertyRepository repository = PropertyRepository.from(List.of(length, uppercase, hash));
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int iteration = 0; iteration < 100; iteration++) {
                        assertEquals(List.of(length, uppercase, hash), repository.getAll(String.class));
                        assertSame(length, repository.get(String.class, "length").orElseThrow());
                        assertEquals(3, repository.get(String.class, "length", Integer.class)
                                .orElseThrow().get("abc"));
                        assertEquals(List.of(length, hash), repository.get(String.class, Integer.class));
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
