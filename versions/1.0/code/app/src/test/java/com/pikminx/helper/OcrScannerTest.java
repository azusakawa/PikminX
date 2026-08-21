package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public final class OcrScannerTest {
    @Test
    public void declaresEveryBundledOnDeviceScript() {
        assertEquals(List.of("Latin", "Chinese", "Devanagari", "Japanese", "Korean"),
                OcrScanner.supportedScriptNames());
    }
}
