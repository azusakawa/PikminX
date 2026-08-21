package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RemoteConfigClientTest {
    @Test
    public void forcedUpdateBlocksOnlyOlderVersions() {
        RemoteConfigClient.Status status = new RemoteConfigClient.Status(
                2, "enabled", "ok", 201, "2.0.1", true,
                "https://example.com/app.apk", "2026-08-20T00:00:00Z");

        assertEquals(2, status.configVersion());
        assertTrue(status.blocksAutomation(200));
        assertFalse(status.blocksAutomation(201));
    }

    @Test
    public void featureFlagsBlockOnlySelectedAutomation() {
        RemoteConfigClient.Status status = new RemoteConfigClient.Status(
                3, "enabled", "maintenance", 201, 202, "2.0.2", false,
                "https://example.com/app.apk", "2026-08-21T00:00:00Z",
                true, false, true, true, true);

        assertTrue(status.featureEnabled(RemoteConfigClient.Feature.PLANTING));
        assertFalse(status.featureEnabled(RemoteConfigClient.Feature.POSTCARD));
        assertTrue(status.blocksAutomation(
                201, RemoteConfigClient.Feature.POSTCARD));
        assertFalse(status.blocksAutomation(
                201, RemoteConfigClient.Feature.PLANTING));
        assertTrue(status.updateAvailable(201));
    }
}
