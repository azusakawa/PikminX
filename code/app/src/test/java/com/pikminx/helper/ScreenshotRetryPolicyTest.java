package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScreenshotRetryPolicyTest {
    @Test
    public void retriesTransientFailuresWithBoundedExponentialBackoff() {
        assertEquals(500L, ScreenshotRetryPolicy.afterFailure(
                ScreenshotRetryPolicy.FailureType.TRANSIENT, 1).delayMillis());
        assertEquals(1000L, ScreenshotRetryPolicy.afterFailure(
                ScreenshotRetryPolicy.FailureType.TRANSIENT, 2).delayMillis());
        assertEquals(2000L, ScreenshotRetryPolicy.afterFailure(
                ScreenshotRetryPolicy.FailureType.TRANSIENT, 3).delayMillis());
        assertEquals(4000L, ScreenshotRetryPolicy.afterFailure(
                ScreenshotRetryPolicy.FailureType.TRANSIENT, 4).delayMillis());
        assertTrue(ScreenshotRetryPolicy.afterFailure(
                ScreenshotRetryPolicy.FailureType.TRANSIENT, 4).retry());
    }

    @Test
    public void stopsAfterTheFailureBudgetIsExhausted() {
        ScreenshotRetryPolicy.Decision decision = ScreenshotRetryPolicy.afterFailure(
                ScreenshotRetryPolicy.FailureType.TRANSIENT, 5);

        assertFalse(decision.retry());
        assertEquals(0L, decision.delayMillis());
    }

    @Test
    public void doesNotRetryNonTransientFailures() {
        ScreenshotRetryPolicy.Decision decision = ScreenshotRetryPolicy.afterFailure(
                ScreenshotRetryPolicy.FailureType.NON_TRANSIENT, 1);

        assertFalse(decision.retry());
        assertEquals(0L, decision.delayMillis());
    }
}
