package com.pikminx.helper;

import java.util.Arrays;

/**
 * Local-only bounded latency samples. Values above the reporting ceiling are
 * clamped and counted separately so diagnostics never expose Long.MAX_VALUE.
 */
public final class BoundedLatencyStats {
    public static final int MAX_SAMPLES = 256;
    public static final long MAX_TRACKED_MILLIS = 60_000L;
    public static final long MAX_COUNT = 1_000_000_000L;

    private final long[] samples = new long[MAX_SAMPLES];
    private int size;
    private int nextIndex;
    private long count;
    private long overflowCount;
    private long lastMillis = -1L;
    private long maxMillis = -1L;

    public BoundedLatencyStats() {}

    public synchronized void add(long durationMillis) {
        if (durationMillis < 0L) {
            return;
        }
        long bounded = durationMillis;
        if (durationMillis > MAX_TRACKED_MILLIS) {
            overflowCount = increment(overflowCount);
            bounded = MAX_TRACKED_MILLIS;
        }
        count = increment(count);
        lastMillis = bounded;
        maxMillis = Math.max(maxMillis, bounded);
        samples[nextIndex] = bounded;
        nextIndex = (nextIndex + 1) % MAX_SAMPLES;
        size = Math.min(size + 1, MAX_SAMPLES);
    }

    public synchronized Snapshot snapshot() {
        long[] retained = new long[size];
        for (int index = 0; index < size; index++) {
            int sourceIndex = size == MAX_SAMPLES
                    ? (nextIndex + index) % MAX_SAMPLES
                    : index;
            retained[index] = samples[sourceIndex];
        }
        Arrays.sort(retained);
        return new Snapshot(
                count,
                retained.length,
                overflowCount,
                lastMillis,
                percentile(retained, 50L),
                percentile(retained, 95L),
                percentile(retained, 99L),
                maxMillis);
    }

    private static long percentile(long[] sortedSamples, long percentile) {
        if (sortedSamples.length == 0) {
            return -1L;
        }
        long rank = Math.max(1L, (sortedSamples.length * percentile + 99L) / 100L);
        return sortedSamples[(int) rank - 1];
    }

    private static long increment(long value) {
        return value >= MAX_COUNT ? MAX_COUNT : value + 1L;
    }

    public record Snapshot(
            long count,
            long retainedCount,
            long overflowCount,
            long lastMillis,
            long p50Millis,
            long p95Millis,
            long p99Millis,
            long maxMillis) {
        public String compact() {
            return "n=" + count
                    + " p50=" + format(p50Millis)
                    + " p95=" + format(p95Millis)
                    + " p99=" + format(p99Millis)
                    + " max=" + format(maxMillis)
                    + " overflow=" + overflowCount
                    + (retainedCount < count ? " retained=" + retainedCount : "");
        }

        private static String format(long value) {
            return value < 0L ? "NA" : value + "ms";
        }
    }
}
