package com.kntrel.mc.underilla.core.profiling.jsonStats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.core.profiling.Measurement;
import com.kntrel.mc.underilla.core.profiling.jsonStats.JsonStatsRecorder.MetricKey;
import com.kntrel.mc.underilla.core.profiling.jsonStats.JsonStatsRecorder.Statistics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonStatsRecorderTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CAPTURED_AT, ZoneOffset.UTC);
    private static final UUID OPERATION_ID = UUID.fromString("1839aee4-9be1-4f8d-b7c6-f32e8d76f91d");

    @TempDir
    Path temporaryDirectory;

    @Test
    void aggregatesMeasurementsAndFlushesAJsonSnapshot() throws Exception {
        Path outputPath = temporaryDirectory.resolve("metrics/stats.json");
        JsonStatsRecorder recorder = new JsonStatsRecorder(outputPath, Duration.ofHours(1), CLOCK);
        try {
            recorder.record(measurement("surface_patch", 10));
            recorder.record(measurement("surface_patch", 30));

            assertEquals(Map.of(
                    new MetricKey(JsonStatsRecorderTest.class.getName(), "surface_patch"),
                    new Statistics(2, 40, 10, 30, 20.0, 10.0)
            ), recorder.snapshot());

            recorder.flush();

            String json = Files.readString(outputPath);
            assertEquals("""
                    {
                      "generatedAt": "2026-08-30T12:00:00Z",
                      "metrics": [
                        {
                          "subject": "%s",
                          "event": "surface_patch",
                          "count": 2,
                          "totalNanos": 40,
                          "minNanos": 10,
                          "maxNanos": 30,
                          "meanNanos": 20.0,
                          "standardDeviationNanos": 10.0
                        }
                      ]
                    }
                    """.formatted(JsonStatsRecorderTest.class.getName()), json);
        } finally {
            recorder.close();
        }
    }

    @Test
    void periodicallyFlushesWithoutRetainingIndividualMeasurements() throws Exception {
        Path outputPath = temporaryDirectory.resolve("periodic.json");
        JsonStatsRecorder recorder = new JsonStatsRecorder(outputPath, Duration.ofMillis(10), CLOCK);
        try {
            recorder.record(measurement("caves_patch", 25));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!Files.exists(outputPath) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            assertTrue(Files.exists(outputPath), "periodic snapshot was not written");
            assertTrue(Files.readString(outputPath).contains("\"event\": \"caves_patch\""));
        } finally {
            recorder.close();
        }
    }

    private static Measurement measurement(String event, long durationNanos) {
        return new Measurement(OPERATION_ID, JsonStatsRecorderTest.class, event, durationNanos, CAPTURED_AT);
    }
}
