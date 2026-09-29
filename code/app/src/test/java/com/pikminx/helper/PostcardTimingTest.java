package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PostcardTimingTest {
    @Test
    public void receiptReturnOcrWaitsFiftyPercentLonger() {
        assertEquals(2700L, PostcardTiming.receiptReturnDelayMillis(1800L));
        assertEquals(1350L, PostcardTiming.receiptReturnDelayMillis(900L));
    }
}
