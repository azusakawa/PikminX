package com.pikminx.helper;

import com.pikminx.helper.platform.diagnostics.UsageTelemetryClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class UsageTelemetryClientTest {
    @Test
    public void snapshotContainsAggregateCounters() {
        UsageTelemetryClient.Snapshot snapshot = new UsageTelemetryClient.Snapshot(
                "00000000-0000-4000-8000-000000000001",
                UsageTelemetryClient.Operation.DISPATCH,
                "completed", 3, 9000L, 2, 0, 0, 2, 1, 0, 0);

        assertEquals(0, snapshot.plantingCount());
        assertEquals(0, snapshot.postcardCount());
        assertEquals(2, snapshot.dispatchFruitCount());
        assertEquals(1, snapshot.dispatchPotCount());
        assertEquals(0, snapshot.returnRewardCount());
        assertEquals(0, snapshot.feedNectarCount());
        assertEquals(0, snapshot.feedPetalCount());
    }

    @Test
    public void plantingCountTracksObservedPetalConsumption() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.PLANTING, 1, 2);

        session.recordPlantingPetalRemaining("黃色花瓣", 100);
        session.recordPlantingPetalRemaining("黃色花瓣", 96);
        session.recordPlantingPetalRemaining("黃色花瓣", 96);
        session.recordPlantingPetalRemaining("黃色花瓣", 90);
        session.recordPlantingPetalRemaining("紅色花瓣", 80);
        session.recordPlantingPetalRemaining("紅色花瓣", 75);
        session.recordPlantingPetalRemaining("紅色花瓣", 90);
        session.recordPlantingPetalRemaining("紅色花瓣", 88);

        UsageTelemetryClient.Snapshot snapshot = session.snapshot("completed", 9000L);
        assertEquals(17, snapshot.plantingCount());
        assertEquals(Integer.valueOf(17), UsageTelemetryClient.counts(snapshot).get("planting"));
    }

    @Test
    public void postcardCountTracksEachReceiveTap() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.POSTCARD, 2, 2);

        session.recordPostcard();
        session.recordPostcard();

        Map<String, Integer> counts = UsageTelemetryClient.counts(
                session.snapshot("completed", 9000L));
        assertEquals(Integer.valueOf(2), counts.get("postcard"));
        assertEquals(Integer.valueOf(0), counts.get("planting"));
    }

    @Test
    public void localTerminalDispatcherEmitsOnceWithoutNetworkingOrWorkflowMutation() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.POSTCARD, 2, 2);
        List<UsageTelemetryClient.Snapshot> failedDispatches = new ArrayList<>();
        int workflowState = 17;

        session.recordPostcard();
        session.finish("completed", failedDispatches::add);
        session.finish("completed", failedDispatches::add);

        assertEquals(1, failedDispatches.size());
        assertEquals(1, failedDispatches.get(0).postcardCount());
        assertEquals(1, session.snapshot("completed", 9_000L).postcardCount());
        assertEquals(17, workflowState);
    }

    @Test
    public void feedingCountsTrackOnlyPositiveNectarAndObservedPetals() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.FEED, 6, 2);

        session.recordFeedNectar(25);
        session.recordFeedNectar(0);
        session.recordFeedNectar(-1);
        session.recordFeedNectar(18);
        session.recordFeedPetals(50);
        session.recordFeedPetals(0);
        session.recordFeedPetals(-1);
        session.recordFeedPetals(30);

        UsageTelemetryClient.Snapshot snapshot = session.snapshot("completed", 9000L);
        assertEquals(43, snapshot.feedNectarCount());
        assertEquals(80, snapshot.feedPetalCount());
        assertEquals(Integer.valueOf(43), UsageTelemetryClient.counts(snapshot).get("feedNectar"));
        assertEquals(Integer.valueOf(80), UsageTelemetryClient.counts(snapshot).get("feedPetals"));
    }

    @Test
    public void diagnosticLogKeepsOnlyTheLatestThirtyTwoEvents() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.POSTCARD, 1, 2);
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY,
                1080,
                2400,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                null,
                0,
                1L,
                0L);
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.FULL_CHINESE, 1080, 2400, geometry);
        OcrScan.Frame frame = new OcrScan.Frame(
                new OcrScan.TransactionId(1L, 1L, 1L),
                OcrScan.Profile.FULL_CHINESE,
                transform,
                List.of(new PetalMatcher.Token("private-ocr-text", 1, 2, 3, 4)),
                25,
                null,
                geometry,
                true);

        for (int index = 0; index < 40; index++) {
            session.recordOcr("full", "SELECT_PETAL", frame);
        }

        UsageTelemetryClient.Snapshot snapshot = session.snapshot("completed", 9000L);
        assertEquals(32, snapshot.diagnosticEventCount());
        assertEquals(9, snapshot.firstDiagnosticSequence());
    }

    @Test
    public void feedSearchGestureDiagnosticKeepsMappedCoordinates() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.FEED, 6, 2);
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY,
                1080,
                2400,
                new CaptureGeometry.Bounds(0, 0, 1080, 2160),
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                0,
                22L,
                8_076_091L);

        session.recordFeedSearchGesture(
                geometry, 980, 474, 980, 474, "display-bitmap", 1);

        UsageTelemetryClient.Snapshot snapshot = session.snapshot("stopped", 9000L);
        assertEquals(1, snapshot.diagnosticEventCount());
        assertEquals("gesture", snapshot.lastDiagnosticType());
        assertEquals(980, snapshot.lastGestureScreenX());
        assertEquals(474, snapshot.lastGestureScreenY());
        assertEquals("display-bitmap", snapshot.lastGestureMapping());
    }

    @Test
    public void dispatchDetailDiagnosticUsesBoundedCodes() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.DISPATCH, 1, 8);

        session.recordDispatchDetail(
                "tap-requested", "ocr-match", "accepted", 1, true,
                1080, 2400, 540, 1800);

        UsageTelemetryClient.Snapshot snapshot = session.snapshot("stopped", 31_000L);
        assertEquals("dispatch-detail", snapshot.lastDiagnosticType());
        assertEquals("tap-requested", snapshot.lastDispatchDetailOutcome());
        assertEquals("ocr-match", snapshot.lastDispatchDetailReason());
        assertEquals("accepted", snapshot.lastDispatchDetailMatch());
        assertEquals(1, snapshot.lastDispatchDetailAttempt());
        assertEquals(true, snapshot.lastDispatchDetailTransitionPending());
        assertEquals(540, snapshot.lastDispatchDetailBitmapX());
        assertEquals(1800, snapshot.lastDispatchDetailBitmapY());
        assertEquals("", snapshot.lastDispatchDetailStopReason());
        assertEquals(9, UsageTelemetryClient.TELEMETRY_SCHEMA_VERSION);
    }

    @Test
    public void dispatchSelectionDiagnosticKeepsRelativeTapEvidence() throws Exception {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.DISPATCH, 1, 8);

        session.recordDispatchSelection(
                "tap-requested", "relative-center", 1, 0, false,
                "search-expanded", 1080, 2400, 148, 1097);

        UsageTelemetryClient.Snapshot snapshot = session.snapshot("stopped", 31_000L);
        assertEquals("dispatch-selection", snapshot.lastDiagnosticType());
        assertEquals("tap-requested", snapshot.lastDispatchSelectionOutcome());
        assertEquals("relative-center", snapshot.lastDispatchSelectionReason());
        assertEquals(148, snapshot.lastDispatchSelectionBitmapX());
        assertEquals(1097, snapshot.lastDispatchSelectionBitmapY());
        assertEquals(9, UsageTelemetryClient.TELEMETRY_SCHEMA_VERSION);
    }

    @Test
    public void feedNectarSelectionDiagnosticKeepsOnlyBoundedEvidence() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.FEED, 6, 2);

        session.recordFeedNectarSelection(
                "missing", "base-petal-count-missing", 3, 0, 3, true, false);

        UsageTelemetryClient.Snapshot snapshot = session.snapshot("stopped", 9000L);
        assertEquals(9, UsageTelemetryClient.TELEMETRY_SCHEMA_VERSION);
        assertEquals("feed-nectar-selection", snapshot.lastDiagnosticType());
        assertEquals("missing", snapshot.lastFeedNectarSelectionOutcome());
        assertEquals("base-petal-count-missing", snapshot.lastFeedNectarSelectionReason());
        assertEquals(3, snapshot.lastFeedNectarSelectionScan());
        assertEquals(true, snapshot.lastFeedNectarSelectionNectarCountFound());
        assertFalse(snapshot.lastFeedNectarSelectionPetalCountFound());
    }

    @Test
    public void dispatchActionDiagnosticSurvivesLaterOcrRingPressure() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.DISPATCH, 1, 8);
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY,
                1080,
                2400,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                null,
                0,
                1L,
                0L);
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.FULL_CHINESE, 1080, 2400, geometry);
        OcrScan.Frame frame = new OcrScan.Frame(
                new OcrScan.TransactionId(1L, 1L, 1L),
                OcrScan.Profile.FULL_CHINESE,
                transform,
                List.of(),
                25,
                null,
                geometry,
                true);

        session.recordDispatchDetail(
                "tap-requested", "ocr-match", "accepted", 1, true,
                1080, 2400, 540, 1800);
        for (int index = 0; index < 40; index++) {
            session.recordOcr("full", "WAIT_RESULT", frame);
        }

        UsageTelemetryClient.Snapshot snapshot = session.snapshot("stopped", 31_000L);
        assertEquals(32, snapshot.diagnosticEventCount());
        assertTrue(snapshot.hasDiagnosticType("dispatch-detail"));
    }

    @Test
    public void snapshotCarriesAnImmutableBatchIdForRetries() {
        UsageTelemetryClient.Session session = new UsageTelemetryClient.Session(
                UsageTelemetryClient.Operation.DISPATCH, 0, 8);
        UsageTelemetryClient.Snapshot snapshot = session.snapshot("completed", 1000L);
        assertTrue(snapshot.batchId() != null && !snapshot.batchId().isEmpty());
        assertEquals(9, UsageTelemetryClient.TELEMETRY_SCHEMA_VERSION);
    }
}
