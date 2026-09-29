package com.pikminx.helper;

import com.pikminx.helper.platform.config.RemoteConfigStateModel;
import com.pikminx.helper.platform.update.InstallStateModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Covers the cross-lifecycle state contracts without opening Android Activity/PackageInstaller. */
public final class RemoteInstallerCoordinationTest {
    @Test
    public void activityAndServiceShareOneConfigFetchAndRecreationReadsItsResult() {
        ConfigCoordinator coordinator = new ConfigCoordinator();
        long activityRequest = coordinator.fetch("activity", 100L);
        long serviceRequest = coordinator.fetch("service", 101L);

        assertEquals(activityRequest, serviceRequest);
        assertEquals(1, coordinator.networkRequestCount());
        assertEquals(RemoteConfigStateModel.State.LOADING, coordinator.snapshot().state());

        coordinator.complete(activityRequest, true, 8, 150L, "");
        RemoteConfigStateModel.Snapshot recreatedActivityState = coordinator.snapshot();

        assertEquals(RemoteConfigStateModel.State.VALID, recreatedActivityState.state());
        assertEquals(8, recreatedActivityState.configVersion());
        assertTrue(RemoteConfigStateModel.isFresh(recreatedActivityState, 200L, 300_000L));
    }

    @Test
    public void staleConfigCompletionCannotReplaceNewerRequest() {
        ConfigCoordinator coordinator = new ConfigCoordinator();
        long first = coordinator.fetch("activity", 100L);
        coordinator.complete(first, true, 8, 150L, "");
        long newer = coordinator.fetch("service", 400_000L);

        assertFalse(coordinator.complete(first, true, 9, 400_100L, "old"));
        assertEquals(newer, coordinator.snapshot().requestId());
        assertEquals(RemoteConfigStateModel.State.LOADING, coordinator.snapshot().state());
    }

    @Test
    public void validCacheSurvivesTransientReconnectFailureAndTtlIsExplicit() {
        ConfigCoordinator coordinator = new ConfigCoordinator();
        long first = coordinator.fetch("activity", 100L);
        coordinator.complete(first, true, 8, 150L, "");
        long reconnect = coordinator.fetch("service", 400_000L);
        coordinator.complete(reconnect, false, 0, 400_100L, "offline");

        assertEquals(RemoteConfigStateModel.State.STALE_USING_CACHE,
                coordinator.snapshot().state());
        assertEquals(8, coordinator.snapshot().configVersion());
        assertFalse(RemoteConfigStateModel.isFresh(
                coordinator.snapshot(), 400_000L, 300_000L));
    }

    @Test
    public void installerRecreationWaitsForReceiverAndStaleSessionCannotOverwriteIt() {
        InstallStateModel.Snapshot committed = InstallStateModel.committed(
                InstallStateModel.verified(InstallStateModel.start(10L)), 41);
        InstallStateModel.Snapshot recreated = InstallStateModel.restore(
                committed.attemptId(), committed.sessionId(), committed.state(), committed.message());
        InstallStateModel.CallbackResult awaiting = InstallStateModel.applyCallback(
                recreated, 10L, 41, InstallStateModel.CallbackStatus.PENDING_USER_ACTION,
                true, "confirm");
        InstallStateModel.Snapshot installing = awaiting.state();

        assertEquals(InstallStateModel.State.COMMITTED, recreated.state());
        assertEquals(InstallStateModel.State.AWAITING_CONFIRMATION, installing.state());
        assertTrue(InstallStateModel.isInFlight(installing));

        InstallStateModel.Snapshot newer = InstallStateModel.committed(
                InstallStateModel.verified(InstallStateModel.start(11L)), 42);
        InstallStateModel.CallbackResult stale = InstallStateModel.applyCallback(
                newer, 10L, 41, InstallStateModel.CallbackStatus.FAILURE, false, "old");

        assertFalse(stale.accepted());
        assertEquals(11L, stale.state().attemptId());
        assertEquals(InstallStateModel.State.COMMITTED, stale.state().state());
    }

    @Test
    public void installerSuccessOrFailureIsOnlyAcceptedFromTheMatchingReceiverSession() {
        InstallStateModel.Snapshot committed = InstallStateModel.committed(
                InstallStateModel.verified(InstallStateModel.start(12L)), 51);
        InstallStateModel.CallbackResult success = InstallStateModel.applyCallback(
                committed, 12L, 51, InstallStateModel.CallbackStatus.SUCCESS, false, "");
        InstallStateModel.CallbackResult withoutActive = InstallStateModel.applyCallback(
                InstallStateModel.idle(), 12L, 51, InstallStateModel.CallbackStatus.SUCCESS,
                false, "late");

        assertTrue(success.accepted());
        assertEquals(InstallStateModel.State.SUCCESS, success.state().state());
        assertFalse(withoutActive.accepted());
        assertEquals(InstallStateModel.State.IDLE, withoutActive.state().state());
    }

    private static final class ConfigCoordinator {
        private RemoteConfigStateModel.Snapshot snapshot = RemoteConfigStateModel.initial();
        private long nextRequestId;
        private int networkRequestCount;

        long fetch(String owner, long nowMillis) {
            RemoteConfigStateModel.StartResult result = RemoteConfigStateModel.beginFetch(
                    snapshot, ++nextRequestId, nowMillis);
            snapshot = result.snapshot();
            if (result.started()) {
                networkRequestCount++;
                return nextRequestId;
            }
            return snapshot.requestId();
        }

        boolean complete(
                long requestId,
                boolean success,
                int configVersion,
                long completedAtMillis,
                String error) {
            RemoteConfigStateModel.CompletionResult result =
                    RemoteConfigStateModel.completeFetch(
                            snapshot,
                            requestId,
                            success,
                            configVersion,
                            completedAtMillis,
                            error);
            if (result.accepted()) {
                snapshot = result.snapshot();
            }
            return result.accepted();
        }

        RemoteConfigStateModel.Snapshot snapshot() { return snapshot; }
        int networkRequestCount() { return networkRequestCount; }
    }
}
