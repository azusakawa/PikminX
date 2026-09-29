package com.pikminx.helper;

import com.pikminx.helper.platform.config.RemoteConfigClient;

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

    @Test
    public void featureAdmissionKeepsWorkflowStartDecisionSeparateFromWorkflowState() {
        RemoteConfigClient.Status allowed = new RemoteConfigClient.Status(
                5, "enabled", "ok", 337, 337, "3.1.27", false,
                "", "2026-09-15T00:00:00Z", true, true, true, true, true);
        RemoteConfigClient.Status denied = new RemoteConfigClient.Status(
                5, "enabled", "dispatch disabled", 337, 337, "3.1.27", false,
                "", "2026-09-15T00:00:00Z", true, true, false, true, true);

        assertFalse(allowed.blocksAutomation(337, RemoteConfigClient.Feature.DISPATCH));
        assertTrue(denied.blocksAutomation(337, RemoteConfigClient.Feature.DISPATCH));
        assertFalse(denied.blocksAutomation(337, RemoteConfigClient.Feature.PLANTING));
    }

    @Test
    public void updateArtifactRequiresValidSha256() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        RemoteConfigClient.Status status = new RemoteConfigClient.Status(
                4, "enabled", "ok", 206, 207, "2.0.5", false,
                "https://example.com/app.apk", hash, "2026-08-26T00:00:00Z",
                true, true, true, true, true);

        assertEquals(hash, status.sha256());
        assertTrue(status.hasInstallableUpdate(206));
        assertTrue(RemoteConfigClient.isValidSha256(hash));
        assertFalse(RemoteConfigClient.isValidSha256("invalid"));
    }
}
