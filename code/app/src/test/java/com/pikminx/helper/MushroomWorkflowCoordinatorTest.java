package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Focused ownership regressions for the extracted normal Mushroom scan coordinator. */
public final class MushroomWorkflowCoordinatorTest {
    @Test
    public void explicitStartSchedulesTheInitialBoundedScanAndPublishesStarting() {
        RecordingScheduler scheduler = new RecordingScheduler();
        RecordingPublisher publisher = new RecordingPublisher();
        MushroomWorkflowCoordinator coordinator = new MushroomWorkflowCoordinator(scheduler, publisher);

        MushroomWorkflowCoordinator.Session session = coordinator.startNormal(41L, "started");

        assertTrue(coordinator.isActive());
        assertTrue(coordinator.normalScanEnabled());
        assertEquals(MushroomWorkflowState.STARTING, coordinator.state());
        assertEquals(1, session.sessionId());
        assertEquals(MushroomWorkflowCoordinator.INITIAL_SCAN_DELAY_MILLIS,
                scheduler.lastDelayMillis());
        assertEquals(MushroomUiState.ScanStatus.STARTING, publisher.lastStatus());
    }

    @Test
    public void onlyOneCaptureCanBeInFlightAndACompletedNormalResultUsesThreeSeconds() {
        RecordingScheduler scheduler = new RecordingScheduler();
        RecordingPublisher publisher = new RecordingPublisher();
        MushroomWorkflowCoordinator coordinator = new MushroomWorkflowCoordinator(scheduler, publisher);
        MushroomWorkflowCoordinator.Session session = coordinator.startNormal(42L, "started");

        assertTrue(coordinator.beginCapture(session, "capturing"));
        assertFalse(coordinator.beginCapture(session, "capturing again"));
        assertTrue(coordinator.completeResult(session, "no result", List.of()));

        assertEquals(MushroomWorkflowState.RESULTS, coordinator.state());
        assertEquals(MushroomWorkflowCoordinator.NORMAL_SCAN_INTERVAL_MILLIS,
                scheduler.lastDelayMillis());
        assertEquals(MushroomUiState.ScanStatus.NO_RESULTS, publisher.lastStatus());
    }

    @Test
    public void stopCancelsFutureScanAndLateS1ResultCannotOverwriteNewS2() {
        RecordingScheduler scheduler = new RecordingScheduler();
        RecordingPublisher publisher = new RecordingPublisher();
        MushroomWorkflowCoordinator coordinator = new MushroomWorkflowCoordinator(scheduler, publisher);
        MushroomWorkflowCoordinator.Session first = coordinator.startNormal(43L, "first");
        assertTrue(coordinator.beginCapture(first, "capturing"));

        coordinator.stop("stopped");
        MushroomWorkflowCoordinator.Session second = coordinator.startNormal(44L, "second");

        assertFalse(coordinator.completeResult(first, "late", List.of()));
        assertEquals(second, coordinator.currentSession());
        assertEquals(MushroomWorkflowState.STARTING, coordinator.state());
        assertEquals(1, scheduler.cancelCount);
        assertEquals(MushroomUiState.ScanStatus.STARTING, publisher.lastStatus());
    }

    @Test
    public void rescanInvalidatesThePriorCaptureWithoutStartingAnotherPeriodicLoop() {
        RecordingScheduler scheduler = new RecordingScheduler();
        RecordingPublisher publisher = new RecordingPublisher();
        MushroomWorkflowCoordinator coordinator = new MushroomWorkflowCoordinator(scheduler, publisher);
        MushroomWorkflowCoordinator.Session first = coordinator.startNormal(45L, "started");
        assertTrue(coordinator.beginCapture(first, "capturing"));

        MushroomWorkflowCoordinator.Session rescan = coordinator.rescan("retry");

        assertFalse(coordinator.completeResult(first, "late", List.of()));
        assertEquals(MushroomUiState.ScanStatus.RETRY_WAIT, publisher.lastStatus());
        assertTrue(coordinator.beginCapture(rescan, "capturing"));
        assertEquals(2, scheduler.scheduleCount());
        assertEquals(1, scheduler.cancelCount);
        assertEquals(0L, scheduler.lastDelayMillis());
    }

    @Test
    public void patrolSuspendsNormalCadenceAndRestoresItOnlyWhenNormalWasEnabled() {
        RecordingScheduler scheduler = new RecordingScheduler();
        RecordingPublisher publisher = new RecordingPublisher();
        MushroomWorkflowCoordinator coordinator = new MushroomWorkflowCoordinator(scheduler, publisher);
        MushroomWorkflowCoordinator.Session normal = coordinator.startNormal(46L, "started");

        coordinator.beginPatrol();
        MushroomWorkflowCoordinator.Session patrol = coordinator.armPatrolScan(46L, "patrol");

        assertFalse(coordinator.beginCapture(normal, "normal"));
        assertTrue(coordinator.beginCapture(patrol, "patrol capture"));
        assertTrue(coordinator.finishPatrol("patrol finished"));
        assertTrue(coordinator.normalScanEnabled());
        assertEquals(MushroomWorkflowCoordinator.NORMAL_SCAN_INTERVAL_MILLIS,
                scheduler.lastDelayMillis());
    }

    private static final class RecordingScheduler implements MushroomWorkflowCoordinator.Scheduler {
        private final List<Long> delays = new ArrayList<>();
        int cancelCount;

        @Override
        public void schedule(long delayMillis) {
            delays.add(delayMillis);
        }

        @Override
        public void cancel() {
            cancelCount++;
        }

        int scheduleCount() {
            return delays.size();
        }

        long lastDelayMillis() {
            return delays.get(delays.size() - 1);
        }
    }

    private static final class RecordingPublisher implements MushroomWorkflowCoordinator.Publisher {
        private final List<Publication> publications = new ArrayList<>();

        @Override
        public void publish(
                MushroomUiState.ScanStatus status,
                String message,
                long sessionId,
                List<MushroomUiState.Result> results) {
            publications.add(new Publication(status, message, sessionId, results));
        }

        MushroomUiState.ScanStatus lastStatus() {
            return publications.get(publications.size() - 1).status();
        }
    }

    private record Publication(
            MushroomUiState.ScanStatus status,
            String message,
            long sessionId,
            List<MushroomUiState.Result> results) {}
}
