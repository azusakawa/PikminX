package com.pikminx.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MultiStageFreshnessTest {
    private static final String GAME_PACKAGE = "com.nianticlabs.pikmin";
    private static final CaptureGeometry.Bounds WINDOW =
            new CaptureGeometry.Bounds(40, 100, 1120, 2500);
    private static final long MAX_FRAME_AGE_MILLIS = 3_000L;

    @Test
    public void longStageCannotReuseAgingFrameButFreshContextCanAuthorizeNextStage() {
        ActionAdmission.FrameContext oldFrame = new ActionAdmission.FrameContext(
                7L, 42L, 17L, 1_000L, GAME_PACKAGE, WINDOW, 12, 0L);
        ActionAdmission.CurrentState afterLongStage = new ActionAdmission.CurrentState(
                true, 7L, 42L, 17L, 4_100L, GAME_PACKAGE, WINDOW, 12, 0L);
        assertFalse(ActionAdmission.allows(oldFrame, afterLongStage, MAX_FRAME_AGE_MILLIS));

        ActionAdmission.FrameContext freshContext = new ActionAdmission.FrameContext(
                7L, 43L, 0L, 4_100L, GAME_PACKAGE, WINDOW, 12, 0L);
        ActionAdmission.CurrentState afterFreshCapture = new ActionAdmission.CurrentState(
                true, 7L, 43L, 17L, 4_200L, GAME_PACKAGE, WINDOW, 12, 0L);
        assertTrue(ActionAdmission.allows(
                freshContext, afterFreshCapture, MAX_FRAME_AGE_MILLIS));
    }
}
