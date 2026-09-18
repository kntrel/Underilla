package com.kntrel.mc.underilla.paper.profiling;

/** An immutable snapshot of one metric, including its population standard deviation. */
public record MetricStatistics(
        long count,
        long totalNanos,
        long minNanos,
        long maxNanos,
        double meanNanos,
        double standardDeviationNanos
) {}
