package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class OcrScanTest {
    @Test
    public void dynamicRoiMapsTokensToTheOriginalCaptureAndRetainsWindowIdentity() {
        CaptureGeometry geometry = new CaptureGeometry(CaptureGeometry.Mode.DISPLAY, 720, 1600,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                new CaptureGeometry.Bounds(0, 120, 1080, 2280), 0, 12L, 100L);
        OcrScan.Transform transform = OcrScan.Transform.forRegion(OcrScan.Profile.TARGETED_CHINESE,
                geometry, new ScreenCoordinateTransform.ScreenshotRect(120, 500, 360, 560));
        assertTrue(geometry.isActionSafe(transform));
        assertEquals(12L, transform.captureSequence());
        assertEquals(80, transform.basisTop());
        assertEquals(320, transform.analysisWidth());
        assertEquals(80, transform.analysisHeight());
        assertEquals(new PetalMatcher.Token("青蘋果", 120, 500, 360, 560),
                transform.toSourceTokens(List.of(new PetalMatcher.Token("青蘋果", 0, 0, 320, 80))).get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void dynamicRoiCannotReadOutsideTheCapturedGameWindow() {
        CaptureGeometry geometry = new CaptureGeometry(CaptureGeometry.Mode.DISPLAY, 720, 1600,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                new CaptureGeometry.Bounds(0, 120, 1080, 2280), 0, 12L, 100L);
        OcrScan.Transform.forRegion(OcrScan.Profile.TARGETED_CHINESE, geometry,
                new ScreenCoordinateTransform.ScreenshotRect(0, 0, 300, 100));
    }

    @Test
    public void narrowDynamicRoiCannotCauseUnboundedUpscaling() {
        CaptureGeometry geometry = new CaptureGeometry(CaptureGeometry.Mode.WINDOW, 720, 1600,
                new CaptureGeometry.Bounds(0, 0, 720, 1600),
                new CaptureGeometry.Bounds(0, 0, 720, 1600), 0, 12L, 100L);
        OcrScan.Transform transform = OcrScan.Transform.forRegion(OcrScan.Profile.TARGETED_CHINESE,
                geometry, new ScreenCoordinateTransform.ScreenshotRect(10, 100, 11, 1500));
        assertEquals(1280, transform.analysisHeight());
        assertEquals(1, transform.analysisWidth());
        assertTrue(geometry.isActionSafe(transform));
    }

    @Test
    public void mapsFocusedPetalTokensBackToSourceCoordinates() {
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.PETAL_LIST, 720, 1600);
        PetalMatcher.Token source = transform.toSourceTokens(List.of(
                new PetalMatcher.Token("白色花瓣", 144, 160, 432, 240))).get(0);

        assertEquals(72, source.left());
        assertEquals(784, source.top());
        assertEquals(216, source.right());
        assertEquals(824, source.bottom());
        assertFalse(transform.usesSourceBitmap());
    }

    @Test
    public void keepsFullFrameCoordinatesUnchanged() {
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.FULL_CHINESE, 1080, 2400);
        PetalMatcher.Token token = new PetalMatcher.Token("接收", 100, 200, 300, 260);

        assertEquals(List.of(token), transform.toSourceTokens(List.of(token)));
        assertTrue(transform.usesSourceBitmap());
    }

    @Test
    public void normalizesFocusedWidthWithoutUnboundedUpscaling() {
        assertEquals(1440, OcrScan.Transform.create(
                OcrScan.Profile.DISPATCH_LIST, 360, 780).analysisWidth());
        assertEquals(1440, OcrScan.Transform.create(
                OcrScan.Profile.DISPATCH_LIST, 1440, 3120).analysisWidth());
        assertEquals(2160, OcrScan.Transform.create(
                OcrScan.Profile.DISPATCH_LIST, 2880, 6240).analysisWidth());
    }

    @Test
    public void normalizesFullFrameWidthAcrossDevices() {
        assertEquals(1080, OcrScan.Transform.create(
                OcrScan.Profile.FULL_CHINESE, 432, 936).analysisWidth());
        assertEquals(1080, OcrScan.Transform.create(
                OcrScan.Profile.FULL_MULTILINGUAL, 1080, 2400).analysisWidth());
        assertEquals(1440, OcrScan.Transform.create(
                OcrScan.Profile.FULL_CHINESE, 2160, 4800).analysisWidth());
    }

    @Test
    public void mappedDispatchTokenUsesTheNormalTargetDetector() {
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.DISPATCH_LIST, 432, 936);
        List<PetalMatcher.Token> mapped = transform.toSourceTokens(List.of(
                new PetalMatcher.Token(
                        "粉紅色花苗",
                        transform.analysisX(40),
                        transform.analysisY(550),
                        transform.analysisX(160),
                        transform.analysisY(592))));

        ExpeditionScreenAnalyzer.Target target = ExpeditionScreenAnalyzer.findTarget(
                mapped, ExpeditionTargetMode.FRUIT_AND_POT, 432, 936);

        assertNotNull(target);
        assertEquals(ExpeditionScreenAnalyzer.ItemKind.POT, target.kind());
        assertTrue(Math.abs(target.y() - 550) < 30);
    }

    @Test
    public void readsFocusedPixelsInSourceCoordinates() {
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY,
                432,
                936,
                new CaptureGeometry.Bounds(0, 0, 432, 936),
                null,
                0,
                1L,
                0L);
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.DISPATCH_LIST, 432, 936, geometry);
        OcrScan.Frame frame = new OcrScan.Frame(
                new OcrScan.TransactionId(1L, 1L, 1L),
                OcrScan.Profile.DISPATCH_LIST,
                transform,
                List.of(),
                0L,
                (x, y) -> x * 10_000 + y,
                geometry,
                true);

        assertEquals(
                transform.analysisX(120) * 10_000 + transform.analysisY(500),
                frame.pixelAtSource(120, 500));
        assertEquals(0xFFFFFFFF, frame.pixelAtSource(120, 100));
    }

    @Test
    public void frameRetainsItsOwnCaptureGeometryAcrossAnotherCapture() {
        CaptureGeometry captureA = new CaptureGeometry(
                CaptureGeometry.Mode.WINDOW, 1080, 2400,
                new CaptureGeometry.Bounds(40, 100, 1120, 2500),
                new CaptureGeometry.Bounds(40, 100, 1120, 2500),
                0, 1L, 10L);
        OcrScan.Frame frameA = new OcrScan.Frame(
                new OcrScan.TransactionId(1L, 1L, 1L),
                OcrScan.Profile.FULL_CHINESE,
                OcrScan.Transform.create(
                        OcrScan.Profile.FULL_CHINESE, 1080, 2400, captureA),
                List.of(), 0L, null, captureA, true);
        CaptureGeometry captureB = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY, 720, 1600,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400), null,
                0, 2L, 20L);

        assertSame(captureA, frameA.captureGeometry());
        assertEquals(new ScreenCoordinateTransform.Point(580, 1300),
                ScreenCoordinateTransform.toScreen(540, 1200, frameA.captureGeometry()));
        assertEquals(new ScreenCoordinateTransform.Point(540, 1200),
                ScreenCoordinateTransform.toScreen(360, 800, captureB));
    }

    @Test
    public void frameRetainsSuppliedAdmissionEpoch() {
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY, 1080, 2400,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                null, 0, 1L, 0L);
        OcrScan.Frame frame = new OcrScan.Frame(
                new OcrScan.TransactionId(1L, 1L, 1L), 42L,
                OcrScan.Profile.FULL_CHINESE,
                OcrScan.Transform.create(OcrScan.Profile.FULL_CHINESE, 1080, 2400, geometry),
                List.of(), 0L, null, geometry, true);

        assertEquals(42L, frame.admissionEpoch());
    }

    @Test
    public void resolvesTargetWindowRoiInFullScreenshotCoordinates() {
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY, 720, 1600,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                new CaptureGeometry.Bounds(0, 120, 1080, 2280),
                0, 1L, 100L);
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.PETAL_LIST, 720, 1600, geometry);

        assertFalse(transform.fallbackToScreenshot());
        assertEquals(80, transform.basisTop());
        assertEquals(1520, transform.basisBottom());
        assertEquals(714, transform.cropTop());
        assertEquals(1462, transform.cropTop() + transform.cropHeight());
    }

    @Test
    public void plantingSearchFocusedRoiReachesTargetWindowBottom() {
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY, 720, 1600,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                new CaptureGeometry.Bounds(0, 120, 1080, 2280),
                0, 1L, 100L);
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.PLANTING_SEARCH_RESULTS, 720, 1600, geometry);

        assertEquals(transform.basisBottom(), transform.cropTop() + transform.cropHeight());
    }
}
