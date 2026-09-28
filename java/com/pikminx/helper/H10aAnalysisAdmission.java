package com.pikminx.helper;

import java.util.Objects;

/** Pure admission guard for a worker result that is about to return to the service. */
final class H10aAnalysisAdmission {
    enum RejectionReason {
        NONE,
        INVALID_CONTEXT,
        WORKFLOW_STATE,
        ACTION_ADMISSION
    }

    record Context(ActionAdmission.FrameContext frame, String mode, String step) {}

    record Current(ActionAdmission.CurrentState actionState, String mode, String step) {}

    record Decision(
            boolean allowed,
            RejectionReason reason,
            ActionAdmission.RejectionReason actionReason) {}

    private H10aAnalysisAdmission() {}

    static Decision evaluate(Context context, Current current, long maximumFrameAgeMillis) {
        if (context == null
                || current == null
                || context.frame() == null
                || context.mode() == null
                || context.step() == null
                || current.mode() == null
                || current.step() == null) {
            return rejected(RejectionReason.INVALID_CONTEXT, ActionAdmission.RejectionReason.INVALID_CONTEXT);
        }
        if (!Objects.equals(context.mode(), current.mode())
                || !Objects.equals(context.step(), current.step())) {
            return rejected(RejectionReason.WORKFLOW_STATE, ActionAdmission.RejectionReason.NONE);
        }
        ActionAdmission.Decision action = ActionAdmission.evaluate(
                context.frame(), current.actionState(), maximumFrameAgeMillis);
        if (!action.allowed()) {
            return rejected(RejectionReason.ACTION_ADMISSION, action.reason());
        }
        return new Decision(true, RejectionReason.NONE, ActionAdmission.RejectionReason.NONE);
    }

    private static Decision rejected(
            RejectionReason reason, ActionAdmission.RejectionReason actionReason) {
        return new Decision(false, reason, actionReason);
    }
}
