package com.pikminx.helper;

/** Pure lifecycle guard for an accessibility stroke whose pointer remains down. */
final class FeedHoldLifecycle {
    enum State {
        IDLE,
        STARTING,
        ACTIVE,
        ABORTING,
        TERMINAL_PENDING
    }

    private State state = State.IDLE;

    State state() {
        return state;
    }

    boolean isIdle() {
        return state == State.IDLE;
    }

    boolean isStarting() {
        return state == State.STARTING;
    }

    boolean isContinuedStrokeActive() {
        return state == State.ACTIVE
                || state == State.ABORTING
                || state == State.TERMINAL_PENDING;
    }

    boolean isCleanupPending() {
        return state == State.ABORTING || state == State.TERMINAL_PENDING;
    }

    boolean canContinue() {
        return state == State.ACTIVE;
    }

    boolean beginStart() {
        if (state != State.IDLE) {
            return false;
        }
        state = State.STARTING;
        return true;
    }

    boolean markContinuedStrokeActive() {
        if (state != State.STARTING) {
            return false;
        }
        state = State.ACTIVE;
        return true;
    }

    boolean beginAbort() {
        if (state != State.ACTIVE) {
            return false;
        }
        state = State.ABORTING;
        return true;
    }

    boolean beginTerminalCleanup() {
        if (state != State.ACTIVE && state != State.ABORTING) {
            return false;
        }
        state = State.TERMINAL_PENDING;
        return true;
    }

    boolean resolveTerminalCleanup() {
        if (state != State.TERMINAL_PENDING) {
            return false;
        }
        state = State.IDLE;
        return true;
    }

    /** Resolves local ownership when a safe terminal continuation cannot be sent. */
    boolean abortWithoutTerminal() {
        if (state == State.IDLE) {
            return false;
        }
        state = State.IDLE;
        return true;
    }

    /** A terminal continuation is safe only in the original generation and window. */
    static boolean sameWindowForCleanup(
            ActionAdmission.FrameContext original,
            ActionAdmission.FrameContext current) {
        if (original == null || current == null
                || original.runGeneration() != current.runGeneration()
                || original.packageName() == null
                || !original.packageName().equals(current.packageName())
                || original.windowBounds() == null
                || !original.windowBounds().equals(current.windowBounds())) {
            return false;
        }
        return original.windowId() == current.windowId();
    }
}
