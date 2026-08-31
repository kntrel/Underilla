package com.kntrel.mc.underilla.paper.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kntrel.mc.underilla.core.profiling.Measurement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonStatsRecorderTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CAPTURED_AT, ZoneOffset.UTC);
    private static final UUID FIRST_OPERATION = UUID.fromString("7702e90b-e8ee-4a34-9e7a-cd63c77c6f43");
    private static final UUID SECOND_OPERATION = UUID.fromString("40c0abf7-1be3-455d-9249-a4b127744816");

    @Test
    void aggregatesMeasurementsWithoutRetainingIndividualSamples(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("metrics.json");
        try (JsonStatsRecorder recorder = new JsonStatsRecorder(output, Duration.ofDays(1), CLOCK)) {
            recorder.record(measurement(FIRST_OPERATION, "surface_patch", 10));
            recorder.record(measurement(SECOND_OPERATION, "surface_patch", 20));
            recorder.record(measurement(FIRST_OPERATION, "surface_patch", 30));

            MetricStatistics statistics = recorder.snapshot()
                    .get(new MetricKey(JsonStatsRecorderTest.class.getName(), "surface_patch"));
            assertEquals(3, statistics.count());
            assertEquals(60, statistics.totalNanos());
            assertEquals(10, statistics.minNanos());
            assertEquals(30, statistics.maxNanos());
            assertEquals(20.0, statistics.meanNanos());
            assertEquals(Math.sqrt(200.0 / 3.0), statistics.standardDeviationNanos(), 0.000_000_001);
        }
    }

    @Test
    void flushesOnlyWhenDirtyAndAtomicallyReplacesTheJsonSnapshot(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("nested").resolve("metrics.json");
        try (JsonStatsRecorder recorder = new JsonStatsRecorder(output, Duration.ofDays(1), CLOCK)) {
            assertFalse(recorder.flush());
            assertFalse(Files.exists(output));

            recorder.record(measurement(FIRST_OPERATION, "caves_patch", 25));
            assertTrue(recorder.flush());
            String firstSnapshot = Files.readString(output);
            assertFalse(recorder.flush());
            assertEquals(firstSnapshot, Files.readString(output));

            JsonObject json = JsonParser.parseString(firstSnapshot).getAsJsonObject();
            assertEquals(CAPTURED_AT.toString(), json.get("generatedAt").getAsString());
            JsonObject metric = json.getAsJsonArray("metrics").get(0).getAsJsonObject();
            assertEquals(JsonStatsRecorderTest.class.getName(), metric.get("subject").getAsString());
            assertEquals("caves_patch", metric.get("event").getAsString());
            assertEquals(1, metric.get("count").getAsLong());
            assertEquals(25, metric.get("meanNanos").getAsDouble());
        }
    }

    @Test
    void periodicallyFlushesDirtySnapshots(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("metrics.json");
        try (JsonStatsRecorder recorder = new JsonStatsRecorder(output, Duration.ofMillis(10), CLOCK)) {
            recorder.record(measurement(FIRST_OPERATION, "liquid_patch", 15));

            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (!Files.exists(output) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            assertTrue(Files.exists(output));
            JsonObject json = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
            assertEquals(1, json.getAsJsonArray("metrics").size());
        }
    }

    private static Measurement measurement(UUID operationId, String event, long durationNanos) {
        return new Measurement(operationId, JsonStatsRecorderTest.class, event, durationNanos, CAPTURED_AT);
    }
}
