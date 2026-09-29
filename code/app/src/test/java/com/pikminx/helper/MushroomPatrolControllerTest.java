package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Pure lifecycle coverage for fresh, point-associated Mushroom patrol scans. */
public final class MushroomPatrolControllerTest {
    private static final MapCoordinate A = new MapCoordinate(25.0000, 121.0000);
    private static final MapCoordinate B = new MapCoordinate(25.0050, 121.0050);

    @Test
    public void eachPointRequiresFreshScanAndCompletesInOrder() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        List<PatrolPoint> route = List.of(new PatrolPoint(0, A), new PatrolPoint(1, B));

        controller.start(77L, route);
        assertEquals(MushroomPatrolController.State.MOVING, controller.state());
        assertEquals(1, host.moves.size());
        controller.locationReady(77L, 0);
        controller.stabilizationComplete(77L, 0);
        assertEquals(MushroomPatrolController.State.WAITING_RESULT, controller.state());
        controller.scanResult(77L, 0, List.of(observation(77L, 0, A)));
        assertEquals(2, host.moves.size());
        assertEquals(1, host.moves.get(1).index());
        controller.locationReady(77L, 1);
        controller.stabilizationComplete(77L, 1);
        controller.scanResult(77L, 1, List.of(observation(77L, 1, B)));

        assertEquals(MushroomPatrolController.State.COMPLETED, controller.state());
        assertEquals(2, controller.observations().size());
        assertEquals(List.of(0, 1), host.scanPointIndexes);
    }

    @Test
    public void locationConfirmationMustCompleteBeforeScan() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        controller.start(78L, List.of(new PatrolPoint(0, A)));

        controller.locationRequestSent(78L, 0);
        assertEquals(
                MushroomPatrolController.State.WAITING_LOCATION_CONFIRMATION,
                controller.state());
        assertTrue(host.scanPointIndexes.isEmpty());

        controller.locationConfirmed(78L, 0);
        assertEquals(MushroomPatrolController.State.STABILIZING, controller.state());
        controller.stabilizationComplete(78L, 0);
        assertEquals(List.of(0), host.scanPointIndexes);
    }

    @Test
    public void locationConfirmationFailureStopsWithoutScanning() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        controller.start(79L, List.of(new PatrolPoint(0, A)));
        controller.locationRequestSent(79L, 0);
        controller.locationConfirmationFailure(79L, 0);

        assertEquals(MushroomPatrolController.State.ERROR, controller.state());
        assertTrue(host.scanPointIndexes.isEmpty());
        assertTrue(host.cancelCount > 0);
    }

    @Test
    public void observationForPreviousPointCannotBeAssignedToCurrentPoint() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        controller.start(80L, List.of(new PatrolPoint(0, A), new PatrolPoint(1, B)));
        controller.locationReady(80L, 0);
        controller.stabilizationComplete(80L, 0);
        controller.scanResult(80L, 0, List.of(observation(80L, 0, B)));

        assertTrue(controller.observations().isEmpty());
        assertEquals(MushroomPatrolController.State.MOVING, controller.state());
        assertEquals(2, host.moves.size());
    }

    @Test
    public void pauseWhileWaitingForConfirmationPreventsLateConfirmation() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        controller.start(81L, List.of(new PatrolPoint(0, A)));
        controller.locationRequestSent(81L, 0);
        controller.pause();
        controller.locationConfirmed(81L, 0);

        assertEquals(MushroomPatrolController.State.PAUSED, controller.state());
        assertTrue(host.scanPointIndexes.isEmpty());
    }

    @Test
    public void staleResultCannotAdvanceCurrentPoint() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        controller.start(88L, List.of(new PatrolPoint(0, A), new PatrolPoint(1, B)));
        controller.locationReady(88L, 0);
        controller.stabilizationComplete(88L, 0);

        controller.scanResult(87L, 0, List.of(observation(87L, 0, A)));

        assertEquals(MushroomPatrolController.State.WAITING_RESULT, controller.state());
        assertTrue(controller.observations().isEmpty());
        assertEquals(1, host.moves.size());
    }

    @Test
    public void stalePointCallbackCannotCompleteAReusedSession() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        controller.start(90L, List.of(new PatrolPoint(0, A), new PatrolPoint(1, B)));
        controller.locationReady(90L, 0);
        controller.stabilizationComplete(90L, 0);
        controller.scanFailure(90L, 99);

        assertEquals(MushroomPatrolController.State.WAITING_RESULT, controller.state());
        assertEquals(1, host.scanPointIndexes.size());
    }

    @Test
    public void scanFailureTerminatesPatrolAndLateResultIsIgnored() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        controller.start(91L, List.of(new PatrolPoint(0, A), new PatrolPoint(1, B)));
        controller.locationReady(91L, 0);
        controller.stabilizationComplete(91L, 0);
        controller.scanFailure(91L, 0);
        controller.scanResult(91L, 0, List.of(observation(91L, 0, A)));

        assertEquals(MushroomPatrolController.State.ERROR, controller.state());
        assertTrue(controller.observations().isEmpty());
    }

    @Test
    public void resumeFromStabilizingRequestsFreshScan() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        controller.start(92L, List.of(new PatrolPoint(0, A)));
        controller.locationReady(92L, 0);
        controller.pause();
        controller.resume();

        assertEquals(MushroomPatrolController.State.MOVING, controller.state());
        assertEquals(2, host.moves.size());
        controller.locationReady(92L, 0);
        controller.stabilizationComplete(92L, 0);
        assertEquals(1, host.scanPointIndexes.size());
    }

    @Test
    public void pauseResumeAndStopLeaveNoPendingState() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        controller.start(99L, List.of(new PatrolPoint(0, A)));
        controller.pause();
        assertEquals(MushroomPatrolController.State.PAUSED, controller.state());
        controller.resume();
        assertEquals(MushroomPatrolController.State.MOVING, controller.state());
        controller.stop();
        controller.stop();
        assertEquals(MushroomPatrolController.State.STOPPED, controller.state());
        assertEquals(2, host.cancelCount);
    }

    @Test
    public void userSelectedCoordinateUpdatesOnlyMatchingObservation() {
        FakeHost host = new FakeHost();
        MushroomPatrolController controller = new MushroomPatrolController(host);
        MushroomDetectionId firstId = new MushroomDetectionId(100L, 7L, 0);
        controller.start(100L, List.of(new PatrolPoint(0, A)));
        controller.locationReady(100L, 0);
        controller.stabilizationComplete(100L, 0);
        controller.scanResult(100L, 0, List.of(frameObservation(100L, 0, A, firstId)));

        MapCoordinate selected = new MapCoordinate(25.0010, 121.0010);
        assertTrue(controller.assignMushroomCoordinate(firstId, selected));
        assertEquals(selected, controller.observations().get(0).mushroomCoordinate());
        assertTrue(controller.observations().get(0).canJumpToMushroom());
        assertTrue(!controller.assignMushroomCoordinate(
                new MushroomDetectionId(100L, 7L, 99), selected));
    }

    private static MushroomObservation observation(long session, int index, MapCoordinate point) {
        return new MushroomObservation(
                session, index, point, "一般灰色蘑菇", "normal", 0.56f, 1234L);
    }

    private static MushroomObservation frameObservation(
            long session, int index, MapCoordinate point, MushroomDetectionId detectionId) {
        return new MushroomObservation(
                session,
                index,
                point,
                point,
                4.0f,
                1234L,
                detectionId,
                717,
                663,
                new MushroomScreenBounds(700, 650, 730, 680),
                null,
                MushroomCoordinateSource.UNAVAILABLE,
                -1.0,
                "一般灰色蘑菇",
                "normal",
                0.56f,
                detectionId.captureSequence(),
                2000L);
    }

    private static final class FakeHost implements MushroomPatrolController.Host {
        final List<PatrolPoint> moves = new ArrayList<>();
        final List<Integer> scanPointIndexes = new ArrayList<>();
        int cancelCount;

        @Override
        public void moveTo(long sessionId, PatrolPoint point) {
            moves.add(point);
        }

        @Override
        public void requestFreshScan(long sessionId, PatrolPoint point) {
            scanPointIndexes.add(point.index());
        }

        @Override
        public void cancelPendingWork(long sessionId) {
            cancelCount++;
        }

        @Override
        public void onStateChanged(
                long sessionId, MushroomPatrolController.State state, PatrolPoint point) {}
    }
}
