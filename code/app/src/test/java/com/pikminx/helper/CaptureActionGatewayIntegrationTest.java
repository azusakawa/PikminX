package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Exercises the capture-to-action ownership boundary without Android framework objects. */
public final class CaptureActionGatewayIntegrationTest {
    private static final String GAME = "com.nianticlabs.pikmin";
    private static final CaptureGeometry.Bounds B = new CaptureGeometry.Bounds(0, 0, 100, 100);

    @Test
    public void staleEvidenceCannotReachTheActionPlatform() {
        CapturePlatform capturePlatform = new CapturePlatform();
        List<CaptureCoordinator.Outcome<String, String>> outcomes = new ArrayList<>();
        CaptureCoordinator<String, String> captures = new CaptureCoordinator<>(
                capturePlatform, outcomes::add, 100);
        CaptureCoordinator.Request<String> request = request("A", 1);

        captures.enqueue(request);
        assertTrue(capturePlatform.success(0, request));
        CaptureGeometry geometry = outcomes.get(0).geometry();
        ActionAdmission.FrameContext evidence = new ActionAdmission.FrameContext(
                request.generation(), geometry.captureSequence(), 0,
                geometry.capturedAtUptimeMillis(), GAME, B, geometry.windowId(),
                request.admissionEpoch());
        ActionPlatform actions = new ActionPlatform(new ActionAdmission.CurrentState(
                true, request.generation(), 2, 0, 2, GAME, B, geometry.windowId(),
                request.admissionEpoch()));

        ActionGateway.Result result = new ActionGateway(actions, 3_000L).tap(evidence, "tap");

        assertFalse(result.accepted());
        assertEquals(ActionAdmission.RejectionReason.CAPTURE_SEQUENCE, result.decision().reason());
        assertEquals(0, actions.gestureCalls);
    }

    @Test
    public void lateACallbackLeavesBActiveAndDeliversNoDuplicateFrame() {
        CapturePlatform platform = new CapturePlatform();
        List<CaptureCoordinator.Outcome<String, String>> outcomes = new ArrayList<>();
        CaptureCoordinator<String, String> captures = new CaptureCoordinator<>(
                platform, outcomes::add, 100);
        CaptureCoordinator.Request<String> a = request("A", 1);
        CaptureCoordinator.Request<String> b = request("B", 2);

        captures.enqueue(a);
        captures.enqueue(b);
        assertTrue(platform.success(0, a));
        assertEquals(2, platform.dispatches);
        assertEquals(1, captures.snapshot().depth());

        assertFalse(platform.success(0, a));
        assertEquals(1, outcomes.size());
        assertEquals(1, captures.snapshot().depth());

        assertTrue(platform.success(1, b));
        assertEquals(2, outcomes.size());
        assertEquals(List.of("A", "B"), platform.restored);
    }

    private static CaptureCoordinator.Request<String> request(String value, long sequence) {
        return new CaptureCoordinator.Request<>(value, List.of(), 7,
                CaptureGeometry.Mode.WINDOW, B, B, 0, 2, sequence, 9,
                CaptureCoordinator.Presentation.NONE, 0);
    }

    private static final class CapturePlatform implements CaptureCoordinator.Platform<String, String> {
        final List<CaptureCoordinator.Callback<String>> callbacks = new ArrayList<>();
        final List<String> restored = new ArrayList<>();
        int dispatches;

        @Override public Object prepareOverlay(CaptureCoordinator.Request<String> request) {
            return request.value();
        }
        @Override public void restoreOverlay(CaptureCoordinator.Request<String> request, Object token) {
            restored.add((String) token);
        }
        @Override public void dispatch(
                CaptureCoordinator.Request<String> request,
                CaptureCoordinator.Callback<String> callback) {
            dispatches++;
            callbacks.add(callback);
        }
        @Override public Object schedule(long delayMillis, Runnable timeout) { return timeout; }
        @Override public void cancel(Object handle) { }

        boolean success(int index, CaptureCoordinator.Request<String> request) {
            return callbacks.get(index).success(
                    request.captureSequence(), request.generation(), request.admissionEpoch(),
                    new CaptureCoordinator.Frame<>(request.value(), 100, 100, 1));
        }
    }

    private static final class ActionPlatform implements ActionGateway.Platform {
        private final ActionAdmission.CurrentState state;
        int gestureCalls;

        ActionPlatform(ActionAdmission.CurrentState state) { this.state = state; }
        @Override public ActionAdmission.CurrentState currentState() { return state; }
        @Override public boolean performNodeAction(Object node, int action, Object argument) { return true; }
        @Override public boolean dispatchGesture(Object gesture) { gestureCalls++; return true; }
        @Override public boolean performGlobalAction(int action) { return true; }
    }
}
