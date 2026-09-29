package com.pikminx.helper.platform.update;

/** Pure state transitions for a PackageInstaller attempt. */
public final class InstallStateModel {
    public static final long NO_ATTEMPT_ID = 0L;
    public static final int NO_SESSION_ID = -1;

    public enum State {
        IDLE,
        DOWNLOADING,
        VERIFIED,
        COMMITTED,
        AWAITING_CONFIRMATION,
        INSTALLING,
        SUCCESS,
        FAILURE
    }

    public enum CallbackStatus {
        PENDING_USER_ACTION,
        SUCCESS,
        FAILURE
    }

    private InstallStateModel() {}

    public static Snapshot idle() {
        return new Snapshot(NO_ATTEMPT_ID, NO_SESSION_ID, State.IDLE, "");
    }

    public static Snapshot start(long attemptId) {
        requireAttempt(attemptId);
        return new Snapshot(attemptId, NO_SESSION_ID, State.DOWNLOADING, "");
    }

    public static Snapshot verified(Snapshot current) {
        requireState(current, State.DOWNLOADING);
        return new Snapshot(current.attemptId, current.sessionId, State.VERIFIED, "");
    }

    public static Snapshot withSession(Snapshot current, int sessionId) {
        requireState(current, State.VERIFIED);
        requireSession(sessionId);
        return new Snapshot(current.attemptId, sessionId, current.state, current.message);
    }

    public static Snapshot committed(Snapshot current, int sessionId) {
        requireState(current, State.VERIFIED);
        requireSession(sessionId);
        if (current.sessionId != NO_SESSION_ID && current.sessionId != sessionId) {
            throw new IllegalArgumentException("Install session changed");
        }
        return new Snapshot(current.attemptId, sessionId, State.COMMITTED, "");
    }

    public static Snapshot restore(
            long attemptId, int sessionId, State state, String message) {
        if (state == null) {
            return invalid(attemptId, sessionId, "Invalid persisted install state");
        }
        if (state == State.IDLE) {
            return idle();
        }
        if (attemptId <= NO_ATTEMPT_ID) {
            return invalid(attemptId, sessionId, "Missing install attempt identity");
        }
        if (requiresSession(state) && sessionId == NO_SESSION_ID) {
            return invalid(attemptId, sessionId, "Missing installer session identity");
        }
        return new Snapshot(attemptId, sessionId, state, message == null ? "" : message);
    }

    public static Snapshot invalid(long attemptId, int sessionId, String message) {
        return new Snapshot(
                Math.max(NO_ATTEMPT_ID, attemptId), sessionId, State.FAILURE,
                message == null || message.isEmpty() ? "Invalid install state" : message);
    }

    public static Snapshot failure(Snapshot current, String message) {
        if (current == null || current.attemptId <= NO_ATTEMPT_ID) {
            return invalid(NO_ATTEMPT_ID, NO_SESSION_ID, message);
        }
        return new Snapshot(
                current.attemptId, current.sessionId, State.FAILURE,
                messageOrDefault(message, "Update failed"));
    }

    public static boolean isInFlight(Snapshot snapshot) {
        return snapshot != null && switch (snapshot.state) {
            case DOWNLOADING, VERIFIED, COMMITTED, AWAITING_CONFIRMATION, INSTALLING -> true;
            case IDLE, SUCCESS, FAILURE -> false;
        };
    }

    public static CallbackResult applyCallback(
            Snapshot current,
            long callbackAttemptId,
            int callbackSessionId,
            CallbackStatus callbackStatus,
            boolean confirmationAvailable,
            String message) {
        if (current == null
                || callbackStatus == null
                || !isInFlight(current)
                || callbackAttemptId != current.attemptId
                || callbackSessionId == NO_SESSION_ID
                || callbackSessionId != current.sessionId) {
            return CallbackResult.rejected(current == null ? idle() : current);
        }

        if (callbackStatus == CallbackStatus.PENDING_USER_ACTION) {
            if (!confirmationAvailable) {
                return CallbackResult.accepted(
                        new Snapshot(current.attemptId, current.sessionId, State.FAILURE,
                                messageOrDefault(message, "Installer confirmation is unavailable")),
                        false);
            }
            if (current.state == State.AWAITING_CONFIRMATION) {
                return CallbackResult.accepted(current, false);
            }
            return CallbackResult.accepted(
                    new Snapshot(current.attemptId, current.sessionId,
                            State.AWAITING_CONFIRMATION, messageOrDefault(message, "")),
                    true);
        }

        if (callbackStatus == CallbackStatus.SUCCESS) {
            return CallbackResult.accepted(
                    new Snapshot(current.attemptId, current.sessionId, State.SUCCESS, ""), false);
        }

        return CallbackResult.accepted(
                new Snapshot(current.attemptId, current.sessionId, State.FAILURE,
                        messageOrDefault(message, "PackageInstaller reported installation failure")),
                false);
    }

    private static boolean requiresSession(State state) {
        return state == State.COMMITTED
                || state == State.AWAITING_CONFIRMATION
                || state == State.INSTALLING
                || state == State.SUCCESS;
    }

    private static String messageOrDefault(String message, String fallback) {
        return message == null || message.isEmpty() ? fallback : message;
    }

    private static void requireAttempt(long attemptId) {
        if (attemptId <= NO_ATTEMPT_ID) {
            throw new IllegalArgumentException("Install attempt must be positive");
        }
    }

    private static void requireSession(int sessionId) {
        if (sessionId == NO_SESSION_ID) {
            throw new IllegalArgumentException("Install session is missing");
        }
    }

    private static void requireState(Snapshot current, State expected) {
        if (current == null || current.state != expected) {
            throw new IllegalArgumentException("Unexpected install state");
        }
    }

    public static final class Snapshot {
        private final long attemptId;
        private final int sessionId;
        private final State state;
        private final String message;

        private Snapshot(long attemptId, int sessionId, State state, String message) {
            this.attemptId = attemptId;
            this.sessionId = sessionId;
            this.state = state;
            this.message = message;
        }

        public long attemptId() {
            return attemptId;
        }

        public int sessionId() {
            return sessionId;
        }

        public State state() {
            return state;
        }

        public String message() {
            return message;
        }
    }

    public static final class CallbackResult {
        private final boolean accepted;
        private final boolean launchConfirmation;
        private final Snapshot state;

        private CallbackResult(boolean accepted, boolean launchConfirmation, Snapshot state) {
            this.accepted = accepted;
            this.launchConfirmation = launchConfirmation;
            this.state = state;
        }

        private static CallbackResult accepted(Snapshot state, boolean launchConfirmation) {
            return new CallbackResult(true, launchConfirmation, state);
        }

        private static CallbackResult rejected(Snapshot state) {
            return new CallbackResult(false, false, state);
        }

        public boolean accepted() {
            return accepted;
        }

        public boolean launchConfirmation() {
            return launchConfirmation;
        }

        public Snapshot state() {
            return state;
        }
    }
}
