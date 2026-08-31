package com.kntrel.mc.underilla.paper.profiling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kntrel.mc.underilla.core.profiling.Measurement;
import com.kntrel.mc.underilla.core.profiling.Recorder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps cumulative in-memory statistics and periodically replaces a JSON snapshot on disk.
 * Memory usage grows with unique subject/event pairs rather than recorded measurements.
 */
public final class JsonStatsRecorder implements Recorder, AutoCloseable {

    public static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofMinutes(1);

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonStatsRecorder.class);
    private static final Comparator<MetricKey> KEY_ORDER = Comparator
            .comparing(MetricKey::subject)
            .thenComparing(MetricKey::event);

    private final Path outputPath;
    private final Clock clock;
    private final Gson gson;
    private final ConcurrentMap<String, ConcurrentMap<String, Accumulator>> accumulators = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong flushedRevision = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantLock flushLock = new ReentrantLock();
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> scheduledFlush;

    public JsonStatsRecorder(Path outputPath) {
        this(outputPath, DEFAULT_FLUSH_INTERVAL);
    }

    public JsonStatsRecorder(Path outputPath, Duration flushInterval) {
        this(outputPath, flushInterval, Clock.systemUTC());
    }

    JsonStatsRecorder(Path outputPath, Duration flushInterval, Clock clock) {
        this.outputPath = Objects.requireNonNull(outputPath, "outputPath").toAbsolutePath().normalize();
        if (this.outputPath.getFileName() == null) {
            throw new IllegalArgumentException("outputPath must identify a file");
        }
        Objects.requireNonNull(flushInterval, "flushInterval");
        long flushIntervalNanos;
        try {
            flushIntervalNanos = flushInterval.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("flushInterval is too large", exception);
        }
        if (flushIntervalNanos <= 0) {
            throw new IllegalArgumentException("flushInterval must be positive");
        }

        this.clock = Objects.requireNonNull(clock, "clock");
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform()
                .daemon(true)
                .name("underilla-json-stats-recorder")
                .unstarted(task));
        this.scheduledFlush = scheduler.scheduleWithFixedDelay(
                this::flushSafely,
                flushIntervalNanos,
                flushIntervalNanos,
                TimeUnit.NANOSECONDS);
    }

    @Override
    public void record(Measurement measurement) {
        Objects.requireNonNull(measurement, "measurement");
        if (closed.get()) {
            throw new IllegalStateException("recorder is closed");
        }

        accumulators
                .computeIfAbsent(measurement.subject().getName(), _ -> new ConcurrentHashMap<>())
                .computeIfAbsent(measurement.event(), _ -> new Accumulator())
                .add(measurement.durationNanos());
        revision.incrementAndGet();
    }

    /** Returns an immutable cumulative snapshot of all statistics recorded so far. */
    public Map<MetricKey, MetricStatistics> snapshot() {
        List<Map.Entry<MetricKey, MetricStatistics>> entries = new ArrayList<>();
        accumulators.forEach((subject, events) ->
                events.forEach((event, accumulator) ->
                    entries.add(Map.entry(new MetricKey(subject, event), accumulator.snapshot()))
                )
        );
        entries.sort(Map.Entry.comparingByKey(KEY_ORDER));

        Map<MetricKey, MetricStatistics> snapshot = new LinkedHashMap<>();
        entries.forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Atomically replaces the configured JSON file when new measurements exist.
     *
     * @return whether a snapshot was written
     */
    public boolean flush() throws IOException {
        flushLock.lock();
        try {
            long targetRevision = revision.get();
            if (targetRevision == flushedRevision.get()) {
                return false;
            }

            writeSnapshot(snapshot());
            flushedRevision.set(targetRevision);
            return true;
        } finally {
            flushLock.unlock();
        }
    }

    public Path outputPath() {
        return outputPath;
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        scheduledFlush.cancel(false);
        scheduler.shutdown();
        flush();
    }

    private void flushSafely() {
        try {
            flush();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not flush metrics snapshot to {}", outputPath, exception);
        }
    }

    private void writeSnapshot(Map<MetricKey, MetricStatistics> statistics) throws IOException {
        List<JsonMetric> metrics = statistics.entrySet().stream()
                .map(entry -> JsonMetric.from(entry.getKey(), entry.getValue()))
                .toList();
        String json = gson.toJson(new JsonSnapshot(Instant.now(clock).toString(), metrics)) + System.lineSeparator();

        Path parent = outputPath.getParent();
        Files.createDirectories(parent);
        Path temporaryPath = Files.createTempFile(parent, ".underilla-metrics-", ".tmp");
        try {
            Files.writeString(
                    temporaryPath,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(
                        temporaryPath,
                        outputPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private record JsonSnapshot(String generatedAt, List<JsonMetric> metrics) {}

    private record JsonMetric(
            String subject,
            String event,
            long count,
            long totalNanos,
            long minNanos,
            long maxNanos,
            double meanNanos,
            double standardDeviationNanos
    ) {

        private static JsonMetric from(MetricKey key, MetricStatistics statistics) {
            return new JsonMetric(
                    key.subject(),
                    key.event(),
                    statistics.count(),
                    statistics.totalNanos(),
                    statistics.minNanos(),
                    statistics.maxNanos(),
                    statistics.meanNanos(),
                    statistics.standardDeviationNanos());
        }
    }

    private static final class Accumulator {

        private long count;
        private long totalNanos;
        private long minNanos = Long.MAX_VALUE;
        private long maxNanos = Long.MIN_VALUE;
        private double meanNanos;
        private double squaredDeviationNanos;

        private synchronized void add(long durationNanos) {
            count++;
            totalNanos += durationNanos;
            minNanos = Math.min(minNanos, durationNanos);
            maxNanos = Math.max(maxNanos, durationNanos);

            double delta = durationNanos - meanNanos;
            meanNanos += delta / count;
            double deltaAfterMean = durationNanos - meanNanos;
            squaredDeviationNanos += delta * deltaAfterMean;
        }

        private synchronized MetricStatistics snapshot() {
            return new MetricStatistics(
                    count,
                    totalNanos,
                    minNanos,
                    maxNanos,
                    meanNanos,
                    Math.sqrt(squaredDeviationNanos / count));
        }
    }
}
