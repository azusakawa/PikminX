package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ReturnRewardRoiTest {
    private static final CaptureGeometry.Bounds GAME_BOUNDS =
            new CaptureGeometry.Bounds(100, 200, 1100, 2200);

    @Test
    public void remainsDisarmedUntilFirstValidTap() {
        ReturnRewardRoi roi = new ReturnRewardRoi();

        assertFalse(roi.isArmed());
        assertNull(roi.detectorRegion(windowGeometry()));
        assertFalse(roi.armFromScreenTap(99, 1200, GAME_BOUNDS));
        assertFalse(roi.isArmed());
    }

    @Test
    public void firstValidTapArmsRoiAndLaterTapsDoNotMoveIt() {
        ReturnRewardRoi roi = new ReturnRewardRoi();

        assertTrue(roi.armFromScreenTap(600, 1200, GAME_BOUNDS));
        CaptureGeometry.Bounds first = roi.screenBounds();
        assertFalse(roi.armFromScreenTap(800, 1400, GAME_BOUNDS));

        assertEquals(first, roi.screenBounds());
        assertEquals(new CaptureGeometry.Bounds(340, 960, 860, 1440), first);
    }

    @Test
    public void clipsRoiToGameBoundsNearAnEdge() {
        ReturnRewardRoi roi = new ReturnRewardRoi();

        assertTrue(roi.armFromScreenTap(110, 210, GAME_BOUNDS));

        assertEquals(
                new CaptureGeometry.Bounds(100, 200, 620, 680),
                roi.screenBounds());
    }

    @Test
    public void mapsScreenRoiIntoWindowScreenshot() {
        ReturnRewardRoi roi = new ReturnRewardRoi();
        roi.armFromScreenTap(600, 1200, GAME_BOUNDS);

        assertEquals(
                new ReturnRewardDetector.Region(240, 760, 760, 1240),
                roi.detectorRegion(windowGeometry()));
    }

    @Test
    public void mapsScreenRoiIntoScaledDisplayScreenshot() {
        ReturnRewardRoi roi = new ReturnRewardRoi();
        roi.armFromScreenTap(600, 1200, GAME_BOUNDS);
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY,
                500,
                1000,
                GAME_BOUNDS,
                GAME_BOUNDS,
                0,
                1L,
                100L);

        assertEquals(
                new ReturnRewardDetector.Region(120, 380, 380, 620),
                roi.detectorRegion(geometry));
    }

    private static CaptureGeometry windowGeometry() {
        return new CaptureGeometry(
                CaptureGeometry.Mode.WINDOW,
                1000,
                2000,
                GAME_BOUNDS,
                GAME_BOUNDS,
                0,
                1L,
                100L);
    }
}
