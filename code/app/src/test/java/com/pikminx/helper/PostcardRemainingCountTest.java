package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PostcardRemainingCountTest {
    @Test
    public void decrementsExactlyOnceForEachConfirmedReceipt() {
        assertEquals(14, PostcardRemainingCount.afterConfirmedReceipt(15));
        assertEquals(0, PostcardRemainingCount.afterConfirmedReceipt(1));
    }

    @Test
    public void neverDropsBelowZero() {
        assertEquals(0, PostcardRemainingCount.afterConfirmedReceipt(0));
        assertEquals(0, PostcardRemainingCount.afterConfirmedReceipt(-1));
    }
}
