package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ExpeditionRemainingCountTest {
    @Test
    public void decrementsOnceWithoutGoingBelowZero() {
        assertEquals(4, ExpeditionRemainingCount.afterConfirmedDispatch(5));
        assertEquals(0, ExpeditionRemainingCount.afterConfirmedDispatch(1));
        assertEquals(0, ExpeditionRemainingCount.afterConfirmedDispatch(0));
    }
}
