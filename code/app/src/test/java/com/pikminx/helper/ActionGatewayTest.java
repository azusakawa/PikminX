package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class ActionGatewayTest {
    private static final String GAME = "com.nianticlabs.pikmin";
    private static final CaptureGeometry.Bounds WINDOW = new CaptureGeometry.Bounds(0, 0, 100, 200);

    @Test
    public void validNodeAndGlobalAndGestureDispatchOnce() {
        FakePlatform platform = new FakePlatform(state());
        ActionGateway gateway = new ActionGateway(platform, 3_000L);
        ActionAdmission.FrameContext frame = frame();

        assertTrue(gateway.nodeClick(frame, "node", n -> true).accepted());
        assertTrue(gateway.editableFocus(frame, "node", n -> true).accepted());
        assertTrue(gateway.editableClearFocus(frame, "node", n -> true).accepted());
        assertTrue(gateway.editableSetText(frame, "node", "abc", n -> true).accepted());
        assertTrue(gateway.tap(frame, "tap").accepted());
        assertTrue(gateway.path(frame, "path").accepted());
        assertTrue(gateway.continuedGesture(frame, "continued").accepted());
        assertTrue(gateway.gameBack(frame).accepted());
        assertEquals(4, platform.nodeCalls);
        assertEquals(3, platform.gestureCalls);
        assertEquals(1, platform.globalCalls);
    }

    @Test
    public void rejectsAllStaleIdentityAndContextWithoutDispatch() {
        ActionAdmission.FrameContext frame = frame();
        ActionAdmission.CurrentState[] states = {
                state(false, 7, 42, 17, 1_400, GAME, WINDOW, 12, 0),
                state(true, 8, 42, 17, 1_400, GAME, WINDOW, 12, 0),
                state(true, 7, 43, 17, 1_400, GAME, WINDOW, 12, 0),
                state(true, 7, 42, 17, 1_400, "other", WINDOW, 12, 0),
                state(true, 7, 42, 17, 4_500, GAME, WINDOW, 12, 0),
                state(true, 7, 42, 17, 1_400, GAME, WINDOW, 13, 0),
                state(true, 7, 42, 17, 1_400, GAME, new CaptureGeometry.Bounds(1, 0, 100, 200), 12, 0),
                state(true, 7, 42, 17, 1_400, GAME, WINDOW, 12, 1)
        };
        ActionAdmission.RejectionReason[] reasons = {
                ActionAdmission.RejectionReason.NOT_RUNNING,
                ActionAdmission.RejectionReason.GENERATION,
                ActionAdmission.RejectionReason.CAPTURE_SEQUENCE,
                ActionAdmission.RejectionReason.PACKAGE,
                ActionAdmission.RejectionReason.CAPTURE_AGE,
                ActionAdmission.RejectionReason.WINDOW_ID,
                ActionAdmission.RejectionReason.WINDOW_BOUNDS,
                ActionAdmission.RejectionReason.ADMISSION_EPOCH
        };
        for (int i = 0; i < states.length; i++) {
            FakePlatform platform = new FakePlatform(states[i]);
            ActionGateway.Result result = new ActionGateway(platform, 3_000L)
                    .tap(frame, "tap");
            assertFalse(result.accepted());
            assertEquals(reasons[i], result.decision().reason());
            assertEquals(0, platform.totalCalls());
        }
    }

    @Test
    public void rejectsMissingRootOrWindowWithoutDispatch() {
        FakePlatform noRoot = new FakePlatform(null);
        ActionGateway.Result noRootResult = new ActionGateway(noRoot, 3_000L).tap(frame(), "tap");
        assertFalse(noRootResult.accepted());
        assertEquals(ActionAdmission.RejectionReason.INVALID_CONTEXT, noRootResult.decision().reason());
        assertEquals(0, noRoot.totalCalls());

        FakePlatform noWindow = new FakePlatform(
                state(true, 7, 42, 17, 1_400, GAME, null, -1, 0));
        ActionGateway.Result noWindowResult = new ActionGateway(noWindow, 3_000L).tap(frame(), "tap");
        assertFalse(noWindowResult.accepted());
        assertEquals(ActionAdmission.RejectionReason.WINDOW_UNAVAILABLE,
                noWindowResult.decision().reason());
        assertEquals(0, noWindow.totalCalls());
    }

    @Test
    public void invalidEditableNodeNeverReachesPlatform() {
        FakePlatform platform = new FakePlatform(state());
        ActionGateway.Result result = new ActionGateway(platform, 3_000L)
                .editableSetText(frame(), "stale-node", "text", n -> false);
        assertFalse(result.accepted());
        assertEquals(ActionAdmission.RejectionReason.FRAME_NOT_ACTION_SAFE, result.decision().reason());
        assertEquals(0, platform.totalCalls());

        ActionGateway.Result clearFocus = new ActionGateway(platform, 3_000L)
                .editableClearFocus(frame(), "stale-node", n -> false);
        assertFalse(clearFocus.accepted());
        assertEquals(ActionAdmission.RejectionReason.FRAME_NOT_ACTION_SAFE, clearFocus.decision().reason());
        assertEquals(0, platform.totalCalls());
    }

    @Test
    public void observerSeesFinalDecisionAndDispatchFailureIsReturned() {
        FakePlatform platform = new FakePlatform(state());
        AtomicInteger observations = new AtomicInteger();
        ActionGateway gateway = new ActionGateway(platform, 3_000L,
                (kind, frame, decision) -> observations.incrementAndGet());
        platform.gestureResult = false;
        ActionGateway.Result result = gateway.tap(frame(), "tap");
        assertTrue(result.decision().allowed());
        assertFalse(result.accepted());
        assertEquals(1, observations.get());
    }

    @Test
    public void stopIsTerminalAndRejectsWithoutDispatch() {
        FakePlatform platform = new FakePlatform(state());
        ActionGateway gateway = new ActionGateway(platform, 3_000L);
        gateway.stop();
        ActionGateway.Result result = gateway.gameBack(frame());
        assertFalse(result.accepted());
        assertEquals(ActionAdmission.RejectionReason.NOT_RUNNING, result.decision().reason());
        assertEquals(0, platform.totalCalls());
    }

    @Test
    public void exposesLiveAdmissionAndCaptureToScreenConversion() {
        FakePlatform platform = new FakePlatform(state());
        ActionGateway gateway = new ActionGateway(platform, 3_000L);
        assertTrue(gateway.admit(frame()).allowed());
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.WINDOW, 50, 100,
                new CaptureGeometry.Bounds(40, 100, 140, 300),
                new CaptureGeometry.Bounds(40, 100, 140, 300), 0, 1, 1_000);
        ScreenCoordinateTransform.Point point = gateway.toScreen(25, 50, geometry);
        assertEquals(90, point.x());
        assertEquals(200, point.y());
    }

    private static ActionAdmission.FrameContext frame() {
        return new ActionAdmission.FrameContext(7, 42, 17, 1_000, GAME, WINDOW, 12);
    }

    private static ActionAdmission.CurrentState state() {
        return state(true, 7, 42, 17, 1_400, GAME, WINDOW, 12, 0);
    }

    private static ActionAdmission.CurrentState state(
            boolean running, long generation, long capture, long ocr, long now,
            String pkg, CaptureGeometry.Bounds bounds, int windowId, long epoch) {
        return new ActionAdmission.CurrentState(running, generation, capture, ocr, now, pkg, bounds, windowId, epoch);
    }

    private static final class FakePlatform implements ActionGateway.Platform {
        private final ActionAdmission.CurrentState state;
        int nodeCalls;
        int gestureCalls;
        int globalCalls;
        boolean gestureResult = true;

        FakePlatform(ActionAdmission.CurrentState state) { this.state = state; }
        @Override public ActionAdmission.CurrentState currentState() { return state; }
        @Override public boolean performNodeAction(Object node, int action, Object argument) { nodeCalls++; return true; }
        @Override public boolean dispatchGesture(Object gesture) { gestureCalls++; return gestureResult; }
        @Override public boolean performGlobalAction(int action) { globalCalls++; return true; }
        int totalCalls() { return nodeCalls + gestureCalls + globalCalls; }
    }
}
