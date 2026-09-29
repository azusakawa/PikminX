package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BoundedLatencyStatsTest {
    @Test
    public void reportsFiniteNearestRankPercentiles() {
        BoundedLatencyStats stats = new BoundedLatencyStats();
        stats.add(10L);
        stats.add(20L);
        stats.add(30L);
        stats.add(40L);

        BoundedLatencyStats.Snapshot snapshot = stats.snapshot();

        assertEquals(4L, snapshot.count());
        assertEquals(20L, snapshot.p50Millis());
        assertEquals(40L, snapshot.p95Millis());
        assertEquals(40L, snapshot.p99Millis());
        assertEquals(40L, snapshot.maxMillis());
        assertEquals(0L, snapshot.overflowCount());
    }

    @Test
    public void clampsOverflowWithoutUsingAnInfiniteHistogramBucket() {
        BoundedLatencyStats stats = new BoundedLatencyStats();
        stats.add(BoundedLatencyStats.MAX_TRACKED_MILLIS + 1L);

        BoundedLatencyStats.Snapshot snapshot = stats.snapshot();

        assertEquals(1L, snapshot.count());
        assertEquals(BoundedLatencyStats.MAX_TRACKED_MILLIS, snapshot.maxMillis());
        assertEquals(1L, snapshot.overflowCount());
        assertFalse(snapshot.compact().contains("9223372036854775807"));
    }

    @Test
    public void keepsRecentSamplesBounded() {
        BoundedLatencyStats stats = new BoundedLatencyStats();
        for (int index = 0; index < BoundedLatencyStats.MAX_SAMPLES + 20; index++) {
            stats.add(index);
        }

        BoundedLatencyStats.Snapshot snapshot = stats.snapshot();

        assertEquals(BoundedLatencyStats.MAX_SAMPLES + 20L, snapshot.count());
        assertEquals(BoundedLatencyStats.MAX_SAMPLES, snapshot.retainedCount());
        assertTrue(snapshot.compact().contains("retained="));
    }
}
