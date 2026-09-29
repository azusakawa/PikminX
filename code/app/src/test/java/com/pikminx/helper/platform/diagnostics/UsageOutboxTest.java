package com.pikminx.helper.platform.diagnostics;

import android.content.SharedPreferences;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UsageOutboxTest {
    @Test
    public void retainsEveryUnacknowledgedBatchBeyondTheFormerCapacity() {
        Map<String, String> stored = new HashMap<>();
        for (int i = 0; i < 20; i++) stored.put("batch:" + i, "immutable-" + i);
        UsageTelemetryClient.uploadPending(preferences(stored), bytes -> false);
        assertEquals(20, stored.size());
    }

    @Test
    public void timeoutRetryUsesIdenticalBytesAndDeletesOnlyAfterAcceptance() {
        Map<String, String> stored = new HashMap<>(Map.of("batch:a", "{\"batchId\":\"a\"}"));
        SharedPreferences preferences = preferences(stored);
        List<byte[]> attempts = new ArrayList<>();
        UsageTelemetryClient.uploadPending(preferences, bytes -> {
            attempts.add(bytes); throw new IOException("timeout after server may have received bytes");
        });
        assertEquals(1, stored.size());
        UsageTelemetryClient.uploadPending(preferences, bytes -> { attempts.add(bytes); return true; });
        assertArrayEquals(attempts.get(0), attempts.get(1));
        assertTrue(stored.isEmpty());
    }

    @Test
    public void acceptanceDoesNotDeleteOtherUnacknowledgedBatches() {
        Map<String, String> stored = new HashMap<>(Map.of("batch:a", "a", "batch:b", "b"));
        UsageTelemetryClient.uploadPending(preferences(stored), bytes ->
                new String(bytes, StandardCharsets.UTF_8).equals("a"));
        assertEquals(Map.of("batch:b", "b"), stored);
        assertFalse(UsageTelemetryClient.acceptedResponse(409));
        assertFalse(UsageTelemetryClient.acceptedResponse(500));
        assertTrue(UsageTelemetryClient.acceptedResponse(200));
    }

    private static SharedPreferences preferences(Map<String, String> stored) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[] {SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAll")) return new HashMap<>(stored);
                    if (method.getName().equals("edit")) {
                        List<String> removals = new ArrayList<>();
                        return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                                new Class<?>[] {SharedPreferences.Editor.class}, (editor, operation, values) -> {
                                    if (operation.getName().equals("remove")) {
                                        removals.add((String) values[0]); return editor;
                                    }
                                    if (operation.getName().equals("commit")) {
                                        removals.forEach(stored::remove); return true;
                                    }
                                    throw new AssertionError(operation.getName());
                                });
                    }
                    throw new AssertionError(method.getName());
                });
    }
}
