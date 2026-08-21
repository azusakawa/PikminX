package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class OverlayRunStatusTest {
    @Test
    public void joinsStageMessageAndDetailForAccessibility() {
        OverlayRunStatus status = new OverlayRunStatus(
                OverlayRunStatus.Kind.SEARCHING,
                "搜尋中",
                "尋找黃色花瓣",
                "花瓣 3 / 12");

        assertEquals(OverlayRunStatus.Kind.SEARCHING, status.kind());
        assertEquals("搜尋中，尋找黃色花瓣，花瓣 3 / 12", status.accessibilityText());
        assertEquals("搜尋中\n尋找黃色花瓣\n花瓣 3 / 12", status.visibleText());
        assertFalse(status.remainsUntilReplaced());
    }

    @Test
    public void omitsBlankDetailAndKeepsErrorsPersistent() {
        OverlayRunStatus status = new OverlayRunStatus(
                OverlayRunStatus.Kind.ERROR,
                "已停止",
                "找不到可用花瓣",
                "   ");

        assertEquals("已停止，找不到可用花瓣", status.accessibilityText());
        assertTrue(status.remainsUntilReplaced());
    }

    @Test
    public void rejectsMissingVisibleText() {
        assertThrows(IllegalArgumentException.class, () -> new OverlayRunStatus(
                OverlayRunStatus.Kind.RECOGNIZING, " ", "辨識中", ""));
        assertThrows(IllegalArgumentException.class, () -> new OverlayRunStatus(
                OverlayRunStatus.Kind.RECOGNIZING, "OCR", null, ""));
    }
}
