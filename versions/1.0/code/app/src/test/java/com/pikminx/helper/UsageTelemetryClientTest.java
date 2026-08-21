package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class UsageTelemetryClientTest {
    @Test
    public void snapshotContainsAggregateCounters() {
        UsageTelemetryClient.Snapshot snapshot = new UsageTelemetryClient.Snapshot(
                "00000000-0000-4000-8000-000000000001",
                UsageTelemetryClient.Operation.DISPATCH,
                "completed", 3, 9000L, 2, 0, 0, 2, 1, 0);

        assertEquals(0, snapshot.plantingCount());
        assertEquals(0, snapshot.postcardCount());
        assertEquals(2, snapshot.dispatchFruitCount());
        assertEquals(1, snapshot.dispatchPotCount());
        assertEquals(0, snapshot.returnRewardCount());
    }
}
