package com.pikminx.helper;

/** Confirms that the same semantic target remains in the same normalized region. */
final class ObservationStability {
    enum Result { CANDIDATE, STABLE }

    private record Candidate(String key, int x, int y) {}

    private final int requiredConfirmations;
    private final int allowedMissingFrames;
    private final float xTolerance;
    private final float yTolerance;
    private Candidate candidate;
    private int confirmations;
    private int missingFrames;

    ObservationStability(
            int requiredConfirmations,
            int allowedMissingFrames,
            float xTolerance,
            float yTolerance) {
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
        this.allowedMissingFrames = Math.max(0, allowedMissingFrames);
        this.xTolerance = Math.max(0f, xTolerance);
        this.yTolerance = Math.max(0f, yTolerance);
    }

    Result observe(String key, int x, int y, int width, int height) {
        Candidate current = new Candidate(key == null ? "" : key, x, y);
        if (sameCandidate(candidate, current, width, height)) {
            confirmations++;
        } else {
            candidate = current;
            confirmations = 1;
        }
        missingFrames = 0;
        return confirmations >= requiredConfirmations ? Result.STABLE : Result.CANDIDATE;
    }

    void miss() {
        if (candidate == null) {
            return;
        }
        missingFrames++;
        if (missingFrames > allowedMissingFrames) {
            reset();
        }
    }

    int confirmations() {
        return confirmations;
    }

    boolean hasCandidate() {
        return candidate != null;
    }

    void reset() {
        candidate = null;
        confirmations = 0;
        missingFrames = 0;
    }

    private boolean sameCandidate(
            Candidate previous, Candidate current, int width, int height) {
        return previous != null
                && previous.key().equals(current.key())
                && Math.abs(previous.x() - current.x()) <= Math.max(1, width) * xTolerance
                && Math.abs(previous.y() - current.y()) <= Math.max(1, height) * yTolerance;
    }
}
