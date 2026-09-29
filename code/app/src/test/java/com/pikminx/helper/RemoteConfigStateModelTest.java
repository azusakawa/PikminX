package com.pikminx.helper;

import com.pikminx.helper.platform.config.RemoteConfigStateModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RemoteConfigStateModelTest {
    private static final long TTL_MILLIS = 300_000L;

    @Test
    public void onlyOneEquivalentFetchCanStart() {
        RemoteConfigStateModel.StartResult first = RemoteConfigStateModel.beginFetch(
                RemoteConfigStateModel.initial(), 1L, 100L);
        RemoteConfigStateModel.StartResult second = RemoteConfigStateModel.beginFetch(
                first.snapshot(), 2L, 101L);

        assertTrue(first.started());
        assertFalse(second.started());
        assertEquals(1L, second.snapshot().requestId());
        assertEquals(RemoteConfigStateModel.State.LOADING, second.snapshot().state());
    }

    @Test
    public void staleCompletionCannotOverwriteNewerRequest() {
        RemoteConfigStateModel.Snapshot loading = RemoteConfigStateModel.beginFetch(
                RemoteConfigStateModel.initial(), 2L, 100L).snapshot();

        RemoteConfigStateModel.CompletionResult result = RemoteConfigStateModel.completeFetch(
                loading, 1L, true, 8, 150L, "old");

        assertFalse(result.accepted());
        assertEquals(RemoteConfigStateModel.State.LOADING, result.snapshot().state());
        assertEquals(2L, result.snapshot().requestId());
    }

    @Test
    public void transientFailureKeepsPreviousConfigAsStaleCache() {
        RemoteConfigStateModel.Snapshot valid = RemoteConfigStateModel.completeFetch(
                RemoteConfigStateModel.beginFetch(
                        RemoteConfigStateModel.initial(), 1L, 100L).snapshot(),
                1L, true, 8, 150L, "").snapshot();
        RemoteConfigStateModel.Snapshot loading = RemoteConfigStateModel.beginFetch(
                valid, 2L, 400_000L).snapshot();

        RemoteConfigStateModel.CompletionResult result = RemoteConfigStateModel.completeFetch(
                loading, 2L, false, 0, 400_100L, "offline");

        assertTrue(result.accepted());
        assertEquals(RemoteConfigStateModel.State.STALE_USING_CACHE,
                result.snapshot().state());
        assertEquals(8, result.snapshot().configVersion());
        assertEquals("offline", result.snapshot().error());
    }

    @Test
    public void cacheFreshnessUsesExplicitTtl() {
        RemoteConfigStateModel.Snapshot valid = RemoteConfigStateModel.completeFetch(
                RemoteConfigStateModel.beginFetch(
                        RemoteConfigStateModel.initial(), 1L, 100L).snapshot(),
                1L, true, 8, 150L, "").snapshot();

        assertTrue(RemoteConfigStateModel.isFresh(valid, 150L + TTL_MILLIS, TTL_MILLIS));
        assertFalse(RemoteConfigStateModel.isFresh(valid, 151L + TTL_MILLIS, TTL_MILLIS));
    }

    @Test
    public void noCacheFailureIsAnError() {
        RemoteConfigStateModel.Snapshot loading = RemoteConfigStateModel.beginFetch(
                RemoteConfigStateModel.initial(), 1L, 100L).snapshot();

        RemoteConfigStateModel.CompletionResult result = RemoteConfigStateModel.completeFetch(
                loading, 1L, false, 0, 150L, "offline");

        assertTrue(result.accepted());
        assertEquals(RemoteConfigStateModel.State.ERROR, result.snapshot().state());
        assertEquals(-1, result.snapshot().configVersion());
    }
}
