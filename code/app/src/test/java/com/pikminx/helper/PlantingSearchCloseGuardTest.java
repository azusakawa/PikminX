package com.pikminx.helper;

import static org.junit.Assert.*;
import org.junit.Test;

public final class PlantingSearchCloseGuardTest {
    @Test public void doesNotCompleteOnMissingXAlone() {
        var guard = new PlantingSearchCloseGuard(3);
        for (int i=0; i<3; i++)
            assertEquals(PlantingSearchCloseGuard.Action.WAIT,
                    guard.observe(true, false, false, false));
        assertEquals(PlantingSearchCloseGuard.Action.FAIL,
                guard.observe(true, false, false, false));
    }
    @Test public void keyboardOrLostGameCannotCauseCloseTap() {
        var guard = new PlantingSearchCloseGuard(12);
        assertEquals(PlantingSearchCloseGuard.Action.WAIT,
                guard.observe(true, true, true, false));
        assertEquals(PlantingSearchCloseGuard.Action.FAIL,
                guard.observe(false, false, true, false));
    }
    @Test public void closesThenRequiresTwoConfirmedMenuFrames() {
        var guard = new PlantingSearchCloseGuard(12);
        assertEquals(PlantingSearchCloseGuard.Action.TAP_CLOSE,
                guard.observe(true, false, true, false));
        assertEquals(PlantingSearchCloseGuard.Action.WAIT,
                guard.observe(true, false, true, false));
        assertEquals(PlantingSearchCloseGuard.Action.WAIT,
                guard.observe(true, false, false, true));
        assertEquals(PlantingSearchCloseGuard.Action.COMPLETE,
                guard.observe(true, false, false, true));
        guard.reset();
        assertEquals(PlantingSearchCloseGuard.Action.TAP_CLOSE,
                guard.observe(true, false, true, false));
    }
}
