package com.pikminx.helper;

import java.util.Objects;
import java.util.function.Predicate;

/** Final safety gate for game actions; Android dispatch remains in {@link Platform}. */
final class ActionGateway {
    static final int NODE_CLICK = 16;
    static final int ACTION_FOCUS = 1;
    static final int ACTION_CLEAR_FOCUS = 2;
    static final int ACTION_SET_TEXT = 2_097_152;
    static final int GLOBAL_BACK = 1;

    enum Kind {
        NODE_CLICK,
        EDITABLE_FOCUS,
        EDITABLE_CLEAR_FOCUS,
        EDITABLE_SET_TEXT,
        TAP,
        PATH,
        CONTINUED_GESTURE,
        GAME_BACK
    }

    interface Platform {
        ActionAdmission.CurrentState currentState();
        boolean performNodeAction(Object node, int action, Object argument);
        boolean dispatchGesture(Object gesture);
        boolean performGlobalAction(int action);
    }

    @FunctionalInterface
    interface AdmissionObserver {
        void onDecision(Kind kind, ActionAdmission.FrameContext frame, ActionAdmission.Decision decision);
    }

    record Result(Kind kind, ActionAdmission.Decision decision, boolean dispatched) {
        boolean accepted() {
            return decision != null && decision.allowed() && dispatched;
        }
    }

    private final Platform platform;
    private final long maximumFrameAgeMillis;
    private final AdmissionObserver observer;
    private volatile boolean stopped;

    ActionGateway(Platform platform, long maximumFrameAgeMillis) {
        this(platform, maximumFrameAgeMillis, (kind, frame, decision) -> { });
    }

    ActionGateway(
            Platform platform,
            long maximumFrameAgeMillis,
            AdmissionObserver observer) {
        this.platform = Objects.requireNonNull(platform);
        this.maximumFrameAgeMillis = maximumFrameAgeMillis;
        this.observer = observer == null ? (kind, frame, decision) -> { } : observer;
    }

    void stop() {
        stopped = true;
    }

    /** Exposes the live final admission check for service-side pre-action lookups. */
    ActionAdmission.Decision admit(ActionAdmission.FrameContext frame) {
        return decision(frame);
    }

    ScreenCoordinateTransform.Point toScreen(
            int bitmapX, int bitmapY, CaptureGeometry geometry) {
        return ScreenCoordinateTransform.toScreen(bitmapX, bitmapY, geometry);
    }

    Result nodeClick(
            ActionAdmission.FrameContext frame,
            Object node,
            Predicate<Object> liveNode) {
        return node(Kind.NODE_CLICK, frame, node, NODE_CLICK, null, liveNode);
    }

    Result editableFocus(
            ActionAdmission.FrameContext frame,
            Object node,
            Predicate<Object> liveEditableNode) {
        return node(Kind.EDITABLE_FOCUS, frame, node, ACTION_FOCUS, null, liveEditableNode);
    }

    Result editableClearFocus(
            ActionAdmission.FrameContext frame,
            Object node,
            Predicate<Object> liveEditableNode) {
        return node(Kind.EDITABLE_CLEAR_FOCUS, frame, node, ACTION_CLEAR_FOCUS, null,
                liveEditableNode);
    }

    Result editableSetText(
            ActionAdmission.FrameContext frame,
            Object node,
            String text,
            Predicate<Object> liveEditableNode) {
        return node(Kind.EDITABLE_SET_TEXT, frame, node, ACTION_SET_TEXT, text, liveEditableNode);
    }

    Result tap(ActionAdmission.FrameContext frame, Object gesture) {
        return gesture(Kind.TAP, frame, gesture);
    }

    Result path(ActionAdmission.FrameContext frame, Object gesture) {
        return gesture(Kind.PATH, frame, gesture);
    }

    Result continuedGesture(ActionAdmission.FrameContext frame, Object gesture) {
        return gesture(Kind.CONTINUED_GESTURE, frame, gesture);
    }

    Result gameBack(ActionAdmission.FrameContext frame) {
        return dispatch(Kind.GAME_BACK, frame, () -> platform.performGlobalAction(GLOBAL_BACK));
    }

    private Result node(
            Kind kind,
            ActionAdmission.FrameContext frame,
            Object node,
            int action,
            Object argument,
            Predicate<Object> liveNode) {
        ActionAdmission.Decision decision = admit(frame);
        if (decision.allowed() && (liveNode == null || node == null || !liveNode.test(node))) {
            decision = new ActionAdmission.Decision(
                    false, ActionAdmission.RejectionReason.FRAME_NOT_ACTION_SAFE);
        }
        observer.onDecision(kind, frame, decision);
        return new Result(kind, decision,
                ActionAdmission.performIfAllowed(
                        decision, () -> platform.performNodeAction(node, action, argument)));
    }

    private Result gesture(Kind kind, ActionAdmission.FrameContext frame, Object gesture) {
        return dispatch(kind, frame, () -> platform.dispatchGesture(gesture));
    }

    private Result dispatch(Kind kind, ActionAdmission.FrameContext frame, ActionAdmission.Action action) {
        ActionAdmission.Decision decision = admit(frame);
        observer.onDecision(kind, frame, decision);
        return new Result(kind, decision, ActionAdmission.performIfAllowed(decision, action));
    }

    private ActionAdmission.Decision decision(ActionAdmission.FrameContext frame) {
        return stopped
                ? new ActionAdmission.Decision(false, ActionAdmission.RejectionReason.NOT_RUNNING)
                : ActionAdmission.evaluate(frame, platform.currentState(), maximumFrameAgeMillis);
    }
}
