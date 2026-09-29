package com.pikminx.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic test-only clock and scheduler; no wall-clock sleeps are used by workflow tests. */
final class WorkflowTestSupport {
    static final String GAME_PACKAGE = "com.nianticlabs.pikmin";
    static final CaptureGeometry.Bounds GAME_BOUNDS =
            new CaptureGeometry.Bounds(40, 100, 1120, 2500);
    static final int GAME_WINDOW_ID = 12;
    static final long MAX_ACTIONABLE_FRAME_AGE_MILLIS = 3_000L;

    private WorkflowTestSupport() {}

    static ActionAdmission.FrameContext frame(
            long generation,
            long captureSequence,
            long ocrRequestSequence,
            long capturedAtUptimeMillis,
            String packageName,
            CaptureGeometry.Bounds bounds,
            int windowId,
            long admissionEpoch) {
        return new ActionAdmission.FrameContext(
                generation,
                captureSequence,
                ocrRequestSequence,
                capturedAtUptimeMillis,
                packageName,
                bounds,
                windowId,
                admissionEpoch);
    }

    static ActionAdmission.CurrentState current(
            boolean running,
            long generation,
            long newestCaptureSequence,
            long latestOcrRequestSequence,
            long nowUptimeMillis,
            String packageName,
            CaptureGeometry.Bounds bounds,
            int windowId,
            long admissionEpoch) {
        return new ActionAdmission.CurrentState(
                running,
                generation,
                newestCaptureSequence,
                latestOcrRequestSequence,
                nowUptimeMillis,
                packageName,
                bounds,
                windowId,
                admissionEpoch);
    }

    static final class FakeClock {
        private long uptimeMillis;

        FakeClock() {
            this(0L);
        }

        FakeClock(long uptimeMillis) {
            if (uptimeMillis < 0L) {
                throw new IllegalArgumentException("Clock cannot start before zero");
            }
            this.uptimeMillis = uptimeMillis;
        }

        long uptimeMillis() {
            return uptimeMillis;
        }

        void advanceBy(long durationMillis) {
            if (durationMillis < 0L) {
                throw new IllegalArgumentException("Clock cannot move backwards");
            }
            uptimeMillis += durationMillis;
        }
    }

    static final class FakeScheduler {
        private record Task(long dueAtMillis, long order, Runnable runnable) {}

        private final FakeClock clock;
        private final List<Task> tasks = new ArrayList<>();
        private long nextOrder;

        FakeScheduler(FakeClock clock) {
            this.clock = clock;
        }

        void post(Runnable runnable) {
            postDelayed(runnable, 0L);
        }

        void postDelayed(Runnable runnable, long delayMillis) {
            if (runnable == null || delayMillis < 0L) {
                throw new IllegalArgumentException("Runnable and delay are required");
            }
            tasks.add(new Task(clock.uptimeMillis() + delayMillis, ++nextOrder, runnable));
        }

        void removeCallbacks(Runnable runnable) {
            tasks.removeIf(task -> task.runnable() == runnable);
        }

        void clear() {
            tasks.clear();
        }

        int pendingTaskCount() {
            return tasks.size();
        }

        long nextDelayMillis() {
            return tasks.stream()
                    .min(Comparator.comparingLong(Task::dueAtMillis)
                            .thenComparingLong(Task::order))
                    .map(task -> Math.max(0L, task.dueAtMillis() - clock.uptimeMillis()))
                    .orElse(-1L);
        }

        void advanceBy(long durationMillis) {
            clock.advanceBy(durationMillis);
            runDueTasks(100);
        }

        void runDueTasks(int maximumTasks) {
            int executed = 0;
            while (true) {
                Task next = tasks.stream()
                        .filter(task -> task.dueAtMillis() <= clock.uptimeMillis())
                        .min(Comparator.comparingLong(Task::dueAtMillis)
                                .thenComparingLong(Task::order))
                        .orElse(null);
                if (next == null) {
                    return;
                }
                tasks.remove(next);
                if (++executed > maximumTasks) {
                    throw new AssertionError("Scheduler exceeded deterministic task budget");
                }
                next.runnable().run();
            }
        }
    }
}
