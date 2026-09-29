package com.pikminx.helper.platform.config;

/** Pure state transitions for the process-wide remote configuration cache. */
public final class RemoteConfigStateModel {
    public enum State {
        UNINITIALIZED,
        LOADING,
        VALID,
        STALE_USING_CACHE,
        ERROR
    }

    private RemoteConfigStateModel() {}

    public static Snapshot initial() {
        return new Snapshot(State.UNINITIALIZED, 0L, -1, 0L, "");
    }

    public static Snapshot restore(
            State state, long requestId, int configVersion, long fetchedAtMillis, String error) {
        return new Snapshot(
                state == null ? State.ERROR : state,
                Math.max(0L, requestId),
                configVersion,
                Math.max(0L, fetchedAtMillis),
                error == null ? "" : error);
    }

    public static StartResult beginFetch(Snapshot current, long requestId, long nowMillis) {
        if (requestId <= 0L) {
            throw new IllegalArgumentException("Request identity must be positive");
        }
        Snapshot safeCurrent = current == null ? initial() : current;
        if (safeCurrent.state == State.LOADING && safeCurrent.requestId > 0L) {
            return new StartResult(false, safeCurrent);
        }
        return new StartResult(
                true,
                new Snapshot(State.LOADING, requestId, safeCurrent.configVersion,
                        safeCurrent.fetchedAtMillis, ""));
    }

    public static CompletionResult completeFetch(
            Snapshot current,
            long requestId,
            boolean success,
            int configVersion,
            long completedAtMillis,
            String error) {
        if (current == null
                || current.state != State.LOADING
                || current.requestId != requestId) {
            return new CompletionResult(false, current == null ? initial() : current);
        }
        if (success) {
            return new CompletionResult(true, new Snapshot(
                    State.VALID, requestId, configVersion, Math.max(0L, completedAtMillis), ""));
        }
        if (current.configVersion >= 0) {
            return new CompletionResult(true, new Snapshot(
                    State.STALE_USING_CACHE, requestId, current.configVersion,
                    current.fetchedAtMillis, error == null ? "" : error));
        }
        return new CompletionResult(true, new Snapshot(
                State.ERROR, requestId, -1, 0L, error == null ? "" : error));
    }

    public static boolean isFresh(Snapshot snapshot, long nowMillis, long ttlMillis) {
        return snapshot != null
                && snapshot.state == State.VALID
                && snapshot.configVersion >= 0
                && snapshot.fetchedAtMillis > 0L
                && ttlMillis >= 0L
                && nowMillis >= snapshot.fetchedAtMillis
                && nowMillis - snapshot.fetchedAtMillis <= ttlMillis;
    }

    public static final class Snapshot {
        private final State state;
        private final long requestId;
        private final int configVersion;
        private final long fetchedAtMillis;
        private final String error;

        private Snapshot(
                State state, long requestId, int configVersion,
                long fetchedAtMillis, String error) {
            this.state = state;
            this.requestId = requestId;
            this.configVersion = configVersion;
            this.fetchedAtMillis = fetchedAtMillis;
            this.error = error;
        }

        public State state() {
            return state;
        }

        public long requestId() {
            return requestId;
        }

        public int configVersion() {
            return configVersion;
        }

        public long fetchedAtMillis() {
            return fetchedAtMillis;
        }

        public String error() {
            return error;
        }
    }

    public static final class StartResult {
        private final boolean started;
        private final Snapshot snapshot;

        private StartResult(boolean started, Snapshot snapshot) {
            this.started = started;
            this.snapshot = snapshot;
        }

        public boolean started() {
            return started;
        }

        public Snapshot snapshot() {
            return snapshot;
        }
    }

    public static final class CompletionResult {
        private final boolean accepted;
        private final Snapshot snapshot;

        private CompletionResult(boolean accepted, Snapshot snapshot) {
            this.accepted = accepted;
            this.snapshot = snapshot;
        }

        public boolean accepted() {
            return accepted;
        }

        public Snapshot snapshot() {
            return snapshot;
        }
    }
}
