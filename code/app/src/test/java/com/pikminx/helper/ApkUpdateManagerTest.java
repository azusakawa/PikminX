package com.pikminx.helper;

import com.pikminx.helper.platform.update.ApkUpdateManager;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public final class ApkUpdateManagerTest {
    @Test
    public void computesSha256BeforeInstall() throws Exception {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ApkUpdateManager.sha256(new ByteArrayInputStream(
                        "abc".getBytes(StandardCharsets.UTF_8))));
    }
}
