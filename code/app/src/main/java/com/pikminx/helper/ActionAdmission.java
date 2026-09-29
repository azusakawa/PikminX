package com.pikminx.helper;

/** Pure admission policy for actions derived from one captured game frame. */
public final class ActionAdmission {
    public enum RejectionReason {
        NONE,
        INVALID_CONTEXT,
        NOT_RUNNING,
        GENERATION,
        CAPTURE_SEQUENCE,
        OCR_SEQUENCE,
        ADMISSION_EPOCH,
        CAPTURE_AGE,
        PACKAGE,
        WINDOW_UNAVAILABLE,
        WINDOW_BOUNDS,
        WINDOW_ID,
        FRAME_NOT_ACTION_SAFE
    }

    public record Decision(boolean allowed, RejectionReason reason) {}

    @FunctionalInterface
    interface Action {
        boolean perform();
    }

    public record FrameContext(
            long runGeneration,
            long captureSequence,
            long ocrRequestSequence,
            long capturedAtUptimeMillis,
            String packageName,
            CaptureGeometry.Bounds windowBounds,
            int windowId,
            long admissionEpoch) {
        FrameContext(
                long runGeneration,
                long captureSequence,
                long ocrRequestSequence,
                long capturedAtUptimeMillis,
                String packageName,
                CaptureGeometry.Bounds windowBounds,
                int windowId) {
            this(
                    runGeneration,
                    captureSequence,
                    ocrRequestSequence,
                    capturedAtUptimeMillis,
                    packageName,
                    windowBounds,
                    windowId,
                    0L);
        }
    }

    record CurrentState(
            boolean running,
            long runGeneration,
            long newestCaptureSequence,
            long latestOcrRequestSequence,
            long nowUptimeMillis,
            String packageName,
            CaptureGeometry.Bounds windowBounds,
            int windowId,
            long admissionEpoch) {
        CurrentState(
                boolean running,
                long runGeneration,
                long newestCaptureSequence,
                long latestOcrRequestSequence,
                long nowUptimeMillis,
                String packageName,
                CaptureGeometry.Bounds windowBounds,
                int windowId) {
            this(
                    running,
                    runGeneration,
                    newestCaptureSequence,
                    latestOcrRequestSequence,
                    nowUptimeMillis,
                    packageName,
                    windowBounds,
                    windowId,
                    0L);
        }
    }

    private ActionAdmission() {}

    static boolean allows(
            FrameContext frame,
            CurrentState current,
            long maximumFrameAgeMillis) {
        return evaluate(frame, current, maximumFrameAgeMillis).allowed();
    }

    static boolean performIfAllowed(Decision decision, Action action) {
        return decision != null && decision.allowed() && action != null && action.perform();
    }

    static Decision evaluate(
            FrameContext frame,
            CurrentState current,
            long maximumFrameAgeMillis) {
        if (frame == null || current == null) {
            return rejected(RejectionReason.INVALID_CONTEXT);
        }
        if (!current.running()) {
            return rejected(RejectionReason.NOT_RUNNING);
        }
        if (maximumFrameAgeMillis <= 0L
                || frame.runGeneration() < 1L
                || frame.captureSequence() < 1L
                || frame.ocrRequestSequence() < 0L
                || frame.capturedAtUptimeMillis() < 0L
                || current.nowUptimeMillis() < 0L
                || frame.admissionEpoch() < 0L
                || current.admissionEpoch() < 0L) {
            return rejected(RejectionReason.INVALID_CONTEXT);
        }
        if (frame.runGeneration() != current.runGeneration()) {
            return rejected(RejectionReason.GENERATION);
        }
        if (frame.captureSequence() != current.newestCaptureSequence()) {
            return rejected(RejectionReason.CAPTURE_SEQUENCE);
        }
        if (frame.admissionEpoch() != current.admissionEpoch()) {
            return rejected(RejectionReason.ADMISSION_EPOCH);
        }
        // A zero OCR sequence is an explicit capture-only action context used by
        // the keyboard and stage-handoff paths; it still requires every capture/window check.
        if (frame.ocrRequestSequence() > 0L
                && frame.ocrRequestSequence() != current.latestOcrRequestSequence()) {
            return rejected(RejectionReason.OCR_SEQUENCE);
        }
        long ageMillis = current.nowUptimeMillis() - frame.capturedAtUptimeMillis();
        if (ageMillis < 0L || ageMillis > maximumFrameAgeMillis) {
            return rejected(RejectionReason.CAPTURE_AGE);
        }
        if (frame.packageName() == null
                || current.packageName() == null
                || !frame.packageName().equals(current.packageName())) {
            return rejected(RejectionReason.PACKAGE);
        }
        if (frame.windowBounds() == null || current.windowBounds() == null) {
            return rejected(RejectionReason.WINDOW_UNAVAILABLE);
        }
        if (!frame.windowBounds().equals(current.windowBounds())) {
            return rejected(RejectionReason.WINDOW_BOUNDS);
        }
        if (frame.windowId() >= 0 && frame.windowId() != current.windowId()) {
            return rejected(RejectionReason.WINDOW_ID);
        }
        return new Decision(true, RejectionReason.NONE);
    }

    private static Decision rejected(RejectionReason reason) {
        return new Decision(false, reason);
    }
}
