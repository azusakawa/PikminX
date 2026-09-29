package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class GeometryValidationTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void metricsAreNormalizedToBitmapSize() {
        GeometryValidation.Metrics metrics = GeometryValidation.metrics(
                1080,
                2400,
                new ScreenCoordinateTransform.ScreenshotRect(0, 100, 1080, 2200));

        assertEquals(1f, metrics.targetWindowCoverageX(), DELTA);
        assertEquals(0.875f, metrics.targetWindowCoverageY(), DELTA);
        assertEquals(100f / 2400f, metrics.targetOffsetTopRatio(), DELTA);
        assertEquals(200f / 2400f, metrics.targetOffsetBottomRatio(), DELTA);
        assertEquals(0f, metrics.targetOffsetLeftRatio(), DELTA);
        assertEquals(0f, metrics.targetOffsetRightRatio(), DELTA);
    }

    @Test
    public void targetWindowRoiMustStayInsideBitmapAndWindow() {
        CaptureGeometry geometry = displayGeometry();
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.DISPATCH_LIST, 1080, 2400, geometry);
        ScreenCoordinateTransform.ScreenshotRect target =
                ScreenCoordinateTransform.targetWindowInScreenshot(geometry);

        GeometryValidation.RoiSanity sanity =
                GeometryValidation.roiSanity(transform, target);

        assertTrue(sanity.withinBitmap());
        assertTrue(sanity.withinTargetWindow());
        assertTrue(sanity.positiveWidth());
        assertTrue(sanity.positiveHeight());
        assertTrue(sanity.warnings().isEmpty());
    }

    @Test
    public void invalidTargetWindowRoiProducesWarningsOnly() {
        OcrScan.Transform invalid = new OcrScan.Transform(
                100, 200,
                -1, 20, 80, 0,
                100, 100,
                OcrScan.RoiBasis.TARGET_WINDOW,
                0, 10, 100, 190,
                false, "", 0L);

        GeometryValidation.RoiSanity sanity = GeometryValidation.roiSanity(
                invalid,
                new ScreenCoordinateTransform.ScreenshotRect(0, 10, 100, 190));

        assertFalse(sanity.valid());
        assertEquals(
                List.of("ROI_OUTSIDE_BITMAP", "ROI_OUTSIDE_TARGET_WINDOW",
                        "ROI_NON_POSITIVE_HEIGHT"),
                sanity.warnings());
    }

    @Test
    public void windowCaptureWithMatchingSourceResolvesToFullBitmap() {
        CaptureGeometry geometry = new CaptureGeometry(
                CaptureGeometry.Mode.WINDOW,
                1080,
                2400,
                new CaptureGeometry.Bounds(100, 50, 1180, 2450),
                new CaptureGeometry.Bounds(100, 50, 1180, 2450),
                0,
                78L,
                1000L);
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.DISPATCH_LIST, 1080, 2400, geometry);
        OcrScan.Frame frame = new OcrScan.Frame(
                new OcrScan.TransactionId(1L, 78L, 1L),
                OcrScan.Profile.DISPATCH_LIST,
                transform,
                List.of(),
                5L,
                null,
                geometry,
                true);

        GeometryValidation.Snapshot snapshot = GeometryValidation.success(35, frame);

        assertEquals(0, snapshot.resolvedTargetWindow().left());
        assertEquals(0, snapshot.resolvedTargetWindow().top());
        assertEquals(1080, snapshot.resolvedTargetWindow().right());
        assertEquals(2400, snapshot.resolvedTargetWindow().bottom());
        assertEquals(GeometryValidation.Discrepancy.NONE, snapshot.discrepancy());
    }

    @Test
    public void classificationUsesSpecificGeometryEvidenceBeforeUnknown() {
        assertEquals(
                GeometryValidation.Discrepancy.WINDOW_BOUNDS_MISMATCH,
                GeometryValidation.classify(new GeometryValidation.Evidence(
                        false, false, true, true, false, true)));
        assertEquals(
                GeometryValidation.Discrepancy.SCALE_MISMATCH,
                GeometryValidation.classify(new GeometryValidation.Evidence(
                        false, false, true, false, false, true)));
        assertEquals(
                GeometryValidation.Discrepancy.SYSTEMATIC_OFFSET,
                GeometryValidation.classify(new GeometryValidation.Evidence(
                        false, true, false, false, false, false)));
        assertEquals(
                GeometryValidation.Discrepancy.ROI_CONTENT_MISMATCH,
                GeometryValidation.classify(new GeometryValidation.Evidence(
                        false, false, false, false, true, false)));
        assertEquals(
                GeometryValidation.Discrepancy.MINOR,
                GeometryValidation.classify(new GeometryValidation.Evidence(
                        true, false, false, false, false, false)));
        assertEquals(
                GeometryValidation.Discrepancy.UNKNOWN,
                GeometryValidation.classify(new GeometryValidation.Evidence(
                        false, false, false, false, false, true)));
        assertEquals(
                GeometryValidation.Discrepancy.NONE,
                GeometryValidation.classify(new GeometryValidation.Evidence(
                        false, false, false, false, false, false)));
    }

    @Test
    public void serializationContainsGeometryButNeverOcrText() {
        CaptureGeometry geometry = displayGeometry();
        OcrScan.Transform transform = OcrScan.Transform.create(
                OcrScan.Profile.DISPATCH_LIST, 1080, 2400, geometry);
        OcrScan.Frame frame = new OcrScan.Frame(
                new OcrScan.TransactionId(1L, 77L, 1L),
                OcrScan.Profile.DISPATCH_LIST,
                transform,
                List.of(new PetalMatcher.Token("private-ocr-text", 10, 20, 30, 40)),
                25L,
                null,
                geometry,
                true);

        String json = GeometryValidation.success(35, frame).toJson();

        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"captureSequence\":77"));
        assertTrue(json.contains("\"androidSdk\":35"));
        assertTrue(json.contains("\"roiBasis\":\"TARGET_WINDOW\""));
        assertTrue(json.contains("\"ocrTokenCount\":1"));
        assertTrue(json.contains("\"warnings\":[]"));
        assertFalse(json.contains("private-ocr-text"));
    }

    private static CaptureGeometry displayGeometry() {
        return new CaptureGeometry(
                CaptureGeometry.Mode.DISPLAY,
                1080,
                2400,
                new CaptureGeometry.Bounds(0, 0, 1080, 2400),
                new CaptureGeometry.Bounds(0, 100, 1080, 2200),
                0,
                77L,
                1000L);
    }
}
