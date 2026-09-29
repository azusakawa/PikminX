package com.pikminx.helper;

import com.pikminx.helper.platform.update.InstallStateModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class InstallStateModelTest {
    @Test
    public void commitIsNotInstallationSuccess() {
        InstallStateModel.Snapshot committed = committedAttempt(10L, 41);

        assertEquals(InstallStateModel.State.COMMITTED, committed.state());
        assertTrue(InstallStateModel.isInFlight(committed));
    }

    @Test
    public void successCallbackIsAuthoritativeForSuccess() {
        InstallStateModel.Snapshot committed = committedAttempt(10L, 41);

        InstallStateModel.CallbackResult result = InstallStateModel.applyCallback(
                committed, 10L, 41, InstallStateModel.CallbackStatus.SUCCESS, false, "");

        assertTrue(result.accepted());
        assertEquals(InstallStateModel.State.SUCCESS, result.state().state());
    }

    @Test
    public void failureCallbackPreservesFailureReason() {
        InstallStateModel.Snapshot committed = committedAttempt(10L, 41);

        InstallStateModel.CallbackResult result = InstallStateModel.applyCallback(
                committed, 10L, 41, InstallStateModel.CallbackStatus.FAILURE, false,
                "INSTALL_FAILED_ABORTED");

        assertTrue(result.accepted());
        assertEquals(InstallStateModel.State.FAILURE, result.state().state());
        assertEquals("INSTALL_FAILED_ABORTED", result.state().message());
    }

    @Test
    public void confirmationCallbackDoesNotReportSuccess() {
        InstallStateModel.Snapshot committed = committedAttempt(10L, 41);

        InstallStateModel.CallbackResult result = InstallStateModel.applyCallback(
                committed, 10L, 41, InstallStateModel.CallbackStatus.PENDING_USER_ACTION,
                true, "需要使用者確認");

        assertTrue(result.accepted());
        assertTrue(result.launchConfirmation());
        assertEquals(
                InstallStateModel.State.AWAITING_CONFIRMATION, result.state().state());
    }

    @Test
    public void duplicateConfirmationCallbackIsIdempotent() {
        InstallStateModel.Snapshot awaiting = InstallStateModel.applyCallback(
                committedAttempt(10L, 41), 10L, 41,
                InstallStateModel.CallbackStatus.PENDING_USER_ACTION, true, "confirm")
                .state();

        InstallStateModel.CallbackResult result = InstallStateModel.applyCallback(
                awaiting, 10L, 41, InstallStateModel.CallbackStatus.PENDING_USER_ACTION,
                true, "confirm");

        assertTrue(result.accepted());
        assertFalse(result.launchConfirmation());
        assertEquals(InstallStateModel.State.AWAITING_CONFIRMATION, result.state().state());
    }

    @Test
    public void staleCallbackCannotOverwriteNewerAttempt() {
        InstallStateModel.Snapshot newer = committedAttempt(11L, 42);

        InstallStateModel.CallbackResult result = InstallStateModel.applyCallback(
                newer, 10L, 41, InstallStateModel.CallbackStatus.FAILURE, false, "old");

        assertFalse(result.accepted());
        assertEquals(InstallStateModel.State.COMMITTED, result.state().state());
        assertEquals(11L, result.state().attemptId());
    }

    @Test
    public void invalidOrMissingSessionIsRejected() {
        InstallStateModel.Snapshot committed = committedAttempt(10L, 41);

        InstallStateModel.CallbackResult result = InstallStateModel.applyCallback(
                committed, 10L, InstallStateModel.NO_SESSION_ID,
                InstallStateModel.CallbackStatus.SUCCESS, false, "");

        assertFalse(result.accepted());
        assertEquals(InstallStateModel.State.COMMITTED, result.state().state());
    }

    @Test
    public void persistedCommittedStateWithoutSessionBecomesFailure() {
        InstallStateModel.Snapshot restored = InstallStateModel.restore(
                10L, InstallStateModel.NO_SESSION_ID, InstallStateModel.State.COMMITTED, "");

        assertEquals(InstallStateModel.State.FAILURE, restored.state());
        assertEquals("Missing installer session identity", restored.message());
    }

    @Test
    public void persistedSnapshotCanRestoreActiveStateAfterRecreation() {
        InstallStateModel.Snapshot beforeRecreation = committedAttempt(10L, 41);
        InstallStateModel.Snapshot afterRecreation = InstallStateModel.restore(
                beforeRecreation.attemptId(), beforeRecreation.sessionId(),
                beforeRecreation.state(), beforeRecreation.message());

        assertEquals(beforeRecreation.state(), afterRecreation.state());
        assertEquals(beforeRecreation.sessionId(), afterRecreation.sessionId());
        assertTrue(InstallStateModel.isInFlight(afterRecreation));
    }

    @Test
    public void repeatedUpdateCanReuseActiveState() {
        assertTrue(InstallStateModel.isInFlight(
                InstallStateModel.start(10L)));
        assertTrue(InstallStateModel.isInFlight(
                InstallStateModel.verified(InstallStateModel.start(10L))));
        assertTrue(InstallStateModel.isInFlight(
                committedAttempt(10L, 41)));
    }

    private static InstallStateModel.Snapshot committedAttempt(long attemptId, int sessionId) {
        return InstallStateModel.committed(
                InstallStateModel.verified(InstallStateModel.start(attemptId)), sessionId);
    }
}
