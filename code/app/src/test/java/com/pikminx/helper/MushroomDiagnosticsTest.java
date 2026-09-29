package com.pikminx.helper;

import com.pikminx.helper.platform.diagnostics.MushroomDiagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class MushroomDiagnosticsTest {
    @Test
    public void recordsResultsAndKeepsQueueHighWaterBounded() {
        MushroomDiagnostics diagnostics = new MushroomDiagnostics();
        MushroomDetectionResult result = new MushroomDetectionResult(
                List.of(),
                new MushroomDetectionResult.Stats(3, 9, 1, 0, 100, 200, 17));

        for (int index = 0; index < 20; index++) {
            diagnostics.recordQueueState(index, index + 1);
            diagnostics.recordResult(result, 25);
        }
        diagnostics.recordQueueRejected();
        diagnostics.recordStaleResult();

        MushroomDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(20L, snapshot.scans());
        assertEquals(60L, snapshot.redPoints());
        assertEquals(180L, snapshot.comparisons());
        assertEquals(20L, snapshot.queueHighWater());
        assertEquals(1L, snapshot.queueRejected());
        assertEquals(1L, snapshot.staleResults());
        assertTrue(snapshot.detectorLatency().compact().contains("n=20"));
    }

    @Test
    public void resetDropsOldSamplesAndCounters() {
        MushroomDiagnostics diagnostics = new MushroomDiagnostics();
        diagnostics.recordQueueRejected();
        diagnostics.recordStaleResult();
        diagnostics.reset();

        MushroomDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(0L, snapshot.scans());
        assertEquals(0L, snapshot.queueRejected());
        assertEquals(0L, snapshot.staleResults());
        assertEquals(0L, snapshot.detectorLatency().count());
    }
}
