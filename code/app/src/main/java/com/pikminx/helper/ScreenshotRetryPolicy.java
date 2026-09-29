package com.pikminx.helper;

/** Bounded retry policy for transient screenshot callback, timeout and copy failures. */
final class ScreenshotRetryPolicy {
    static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final long BASE_DELAY_MILLIS = 500L;
    private static final long MAX_DELAY_MILLIS = 4000L;

    private ScreenshotRetryPolicy() {}

    enum FailureType {
        TRANSIENT,
        NON_TRANSIENT
    }

    static Decision afterFailure(FailureType failureType, int consecutiveFailures) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType must not be null");
        }
        if (consecutiveFailures <= 0) {
            throw new IllegalArgumentException("consecutiveFailures must be positive");
        }
        if (failureType == FailureType.NON_TRANSIENT
                || consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            return new Decision(false, 0L);
        }
        int shift = Math.min(consecutiveFailures - 1, 3);
        long delay = Math.min(MAX_DELAY_MILLIS, BASE_DELAY_MILLIS << shift);
        return new Decision(true, delay);
    }

    record Decision(boolean retry, long delayMillis) {}
}
