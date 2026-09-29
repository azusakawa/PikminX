package com.pikminx.helper;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** Developer-only, metadata-only validation for one OCR frame's coordinate assumptions. */
final class GeometryValidation {
    static final String LOG_TAG = "PikminXGeometry";
    private static final int SCHEMA_VERSION = 1;
    private static final float SCALE_MISMATCH_RATIO = 0.05f;

    enum Discrepancy {
        NONE,
        MINOR,
        SYSTEMATIC_OFFSET,
        SCALE_MISMATCH,
        WINDOW_BOUNDS_MISMATCH,
        ROI_CONTENT_MISMATCH,
        UNKNOWN
    }

    record Metrics(
            float targetWindowCoverageX,
            float targetWindowCoverageY,
            float targetOffsetTopRatio,
            float targetOffsetBottomRatio,
            float targetOffsetLeftRatio,
            float targetOffsetRightRatio) {}

    record RoiSanity(
            boolean withinBitmap,
            boolean withinTargetWindow,
            boolean positiveWidth,
            boolean positiveHeight) {

        boolean valid() {
            return withinBitmap && withinTargetWindow && positiveWidth && positiveHeight;
        }

        List<String> warnings() {
            List<String> warnings = new ArrayList<>(4);
            if (!withinBitmap) {
                warnings.add("ROI_OUTSIDE_BITMAP");
            }
            if (!withinTargetWindow) {
                warnings.add("ROI_OUTSIDE_TARGET_WINDOW");
            }
            if (!positiveWidth) {
                warnings.add("ROI_NON_POSITIVE_WIDTH");
            }
            if (!positiveHeight) {
                warnings.add("ROI_NON_POSITIVE_HEIGHT");
            }
            return List.copyOf(warnings);
        }
    }

    record Evidence(
            boolean minor,
            boolean systematicOffset,
            boolean scaleMismatch,
            boolean windowBoundsMismatch,
            boolean roiContentMismatch,
            boolean unknown) {}

    record Snapshot(
            int androidSdk,
            OcrScan.Profile profile,
            OcrScan.Transform transform,
            CaptureGeometry captureGeometry,
            ScreenCoordinateTransform.ScreenshotRect resolvedTargetWindow,
            Metrics metrics,
            RoiSanity roiSanity,
            Discrepancy discrepancy,
            int tokenCount,
            long elapsedMillis,
            String outcome,
            String errorClass) {

        String toJson() {
            StringBuilder json = new StringBuilder(1536).append('{');
            field(json, "schemaVersion", SCHEMA_VERSION);
            field(json, "captureSequence", captureGeometry.captureSequence());
            field(json, "androidSdk", androidSdk);
            field(json, "captureMode", captureGeometry.mode().name());
            field(json, "bitmapWidth", captureGeometry.bitmapWidth());
            field(json, "bitmapHeight", captureGeometry.bitmapHeight());
            rect(json, "expectedSourceBoundsOnScreen",
                    captureGeometry.expectedSourceBoundsOnScreen());
            rect(json, "targetWindowBoundsOnScreen",
                    captureGeometry.targetWindowBoundsOnScreen());
            rect(json, "resolvedTargetWindowInScreenshot", resolvedTargetWindow);
            field(json, "profile", profile.name());
            field(json, "roiBasis", transform.roiBasis().name());
            rect(json, "resolvedBasisRectInScreenshot",
                    transform.basisLeft(), transform.basisTop(),
                    transform.basisRight(), transform.basisBottom());
            rect(json, "resolvedFinalRoiInScreenshot",
                    transform.cropLeft(), transform.cropTop(),
                    transform.cropLeft() + transform.cropWidth(),
                    transform.cropTop() + transform.cropHeight());
            field(json, "scaleX", captureGeometry.scaleX());
            field(json, "scaleY", captureGeometry.scaleY());
            field(json, "roiFallback", transform.fallbackToScreenshot());
            field(json, "roiFallbackReason", transform.fallbackReason());
            field(json, "targetWindowCoverageX", metrics.targetWindowCoverageX());
            field(json, "targetWindowCoverageY", metrics.targetWindowCoverageY());
            field(json, "targetOffsetTopRatio", metrics.targetOffsetTopRatio());
            field(json, "targetOffsetBottomRatio", metrics.targetOffsetBottomRatio());
            field(json, "targetOffsetLeftRatio", metrics.targetOffsetLeftRatio());
            field(json, "targetOffsetRightRatio", metrics.targetOffsetRightRatio());
            name(json, "roiSanity");
            json.append('{');
            field(json, "withinBitmap", roiSanity.withinBitmap());
            field(json, "withinTargetWindow", roiSanity.withinTargetWindow());
            field(json, "positiveWidth", roiSanity.positiveWidth());
            field(json, "positiveHeight", roiSanity.positiveHeight());
            name(json, "warnings");
            json.append('[');
            for (String warning : roiSanity.warnings()) {
                if (json.charAt(json.length() - 1) != '[') {
                    json.append(',');
                }
                quoted(json, warning);
            }
            json.append(']').append('}');
            field(json, "discrepancy", discrepancy.name());
            field(json, "ocrTokenCount", tokenCount);
            field(json, "elapsedMillis", elapsedMillis);
            field(json, "outcome", outcome);
            field(json, "errorClass", errorClass);
            return json.append('}').toString();
        }
    }

    static Snapshot success(int androidSdk, OcrScan.Frame frame) {
        return create(
                androidSdk,
                frame.profile(),
                frame.transform(),
                frame.captureGeometry(),
                frame.tokens().size(),
                Math.max(0L, frame.elapsedMillis()),
                "success",
                "");
    }

    static Snapshot failure(
            int androidSdk,
            OcrScan.Profile profile,
            OcrScan.Transform transform,
            CaptureGeometry captureGeometry,
            Exception error) {
        return create(
                androidSdk,
                profile,
                transform,
                captureGeometry,
                0,
                0L,
                "failure",
                error == null ? "Exception" : error.getClass().getSimpleName());
    }

    static Metrics metrics(
            int bitmapWidth,
            int bitmapHeight,
            ScreenCoordinateTransform.ScreenshotRect target) {
        if (target == null) {
            return new Metrics(
                    Float.NaN, Float.NaN, Float.NaN,
                    Float.NaN, Float.NaN, Float.NaN);
        }
        return new Metrics(
                target.width() / (float) bitmapWidth,
                target.height() / (float) bitmapHeight,
                target.top() / (float) bitmapHeight,
                (bitmapHeight - target.bottom()) / (float) bitmapHeight,
                target.left() / (float) bitmapWidth,
                (bitmapWidth - target.right()) / (float) bitmapWidth);
    }

    static RoiSanity roiSanity(
            OcrScan.Transform transform,
            ScreenCoordinateTransform.ScreenshotRect target) {
        int right = transform.cropLeft() + transform.cropWidth();
        int bottom = transform.cropTop() + transform.cropHeight();
        boolean positiveWidth = transform.cropWidth() > 0;
        boolean positiveHeight = transform.cropHeight() > 0;
        boolean withinBitmap = transform.cropLeft() >= 0
                && transform.cropTop() >= 0
                && right <= transform.sourceWidth()
                && bottom <= transform.sourceHeight();
        boolean withinTargetWindow = transform.roiBasis() != OcrScan.RoiBasis.TARGET_WINDOW
                || target != null
                && transform.cropLeft() >= target.left()
                && transform.cropTop() >= target.top()
                && right <= target.right()
                && bottom <= target.bottom();
        return new RoiSanity(
                withinBitmap, withinTargetWindow, positiveWidth, positiveHeight);
    }

    static Discrepancy classify(Evidence evidence) {
        if (evidence.windowBoundsMismatch()) {
            return Discrepancy.WINDOW_BOUNDS_MISMATCH;
        }
        if (evidence.scaleMismatch()) {
            return Discrepancy.SCALE_MISMATCH;
        }
        if (evidence.systematicOffset()) {
            return Discrepancy.SYSTEMATIC_OFFSET;
        }
        if (evidence.roiContentMismatch()) {
            return Discrepancy.ROI_CONTENT_MISMATCH;
        }
        if (evidence.minor()) {
            return Discrepancy.MINOR;
        }
        return evidence.unknown() ? Discrepancy.UNKNOWN : Discrepancy.NONE;
    }

    static void log(Snapshot snapshot) {
        try {
            Log.i(LOG_TAG, snapshot.toJson());
        } catch (RuntimeException ignored) {
            // Validation must never alter OCR behavior.
        }
    }

    private static Snapshot create(
            int androidSdk,
            OcrScan.Profile profile,
            OcrScan.Transform transform,
            CaptureGeometry captureGeometry,
            int tokenCount,
            long elapsedMillis,
            String outcome,
            String errorClass) {
        ScreenCoordinateTransform.ScreenshotRect target =
                ScreenCoordinateTransform.targetWindowInScreenshot(captureGeometry);
        RoiSanity roiSanity = roiSanity(transform, target);
        float scaleX = captureGeometry.scaleX();
        float scaleY = captureGeometry.scaleY();
        float largestScale = Math.max(scaleX, scaleY);
        boolean scaleMismatch = largestScale > 0f
                && Math.abs(scaleX - scaleY) / largestScale > SCALE_MISMATCH_RATIO;
        CaptureGeometry.Bounds targetBounds = captureGeometry.targetWindowBoundsOnScreen();
        boolean targetExpectedForProfile = profile.roiBasis() == OcrScan.RoiBasis.TARGET_WINDOW;
        boolean sourceExpectedToMatchTarget = captureGeometry.mode() == CaptureGeometry.Mode.WINDOW
                && captureGeometry.expectedSourceBoundsOnScreen().equals(targetBounds);
        boolean targetCoversBitmap = target != null
                && target.left() <= 1
                && target.top() <= 1
                && target.right() >= captureGeometry.bitmapWidth() - 1
                && target.bottom() >= captureGeometry.bitmapHeight() - 1;
        boolean windowBoundsMismatch = targetBounds != null && target == null
                || targetExpectedForProfile && targetBounds == null
                || sourceExpectedToMatchTarget && !targetCoversBitmap;
        Discrepancy discrepancy = classify(new Evidence(
                false,
                false,
                scaleMismatch,
                windowBoundsMismatch,
                false,
                !roiSanity.valid()));
        return new Snapshot(
                androidSdk,
                profile,
                transform,
                captureGeometry,
                target,
                metrics(captureGeometry.bitmapWidth(), captureGeometry.bitmapHeight(), target),
                roiSanity,
                discrepancy,
                Math.max(0, tokenCount),
                Math.max(0L, elapsedMillis),
                outcome,
                errorClass);
    }

    private static void rect(
            StringBuilder json, String fieldName, CaptureGeometry.Bounds bounds) {
        if (bounds == null) {
            name(json, fieldName);
            json.append("null");
            return;
        }
        rect(json, fieldName, bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
    }

    private static void rect(
            StringBuilder json,
            String fieldName,
            ScreenCoordinateTransform.ScreenshotRect bounds) {
        if (bounds == null) {
            name(json, fieldName);
            json.append("null");
            return;
        }
        rect(json, fieldName, bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
    }

    private static void rect(
            StringBuilder json, String fieldName, int left, int top, int right, int bottom) {
        name(json, fieldName);
        json.append('{');
        field(json, "left", left);
        field(json, "top", top);
        field(json, "right", right);
        field(json, "bottom", bottom);
        json.append('}');
    }

    private static void field(StringBuilder json, String fieldName, String value) {
        name(json, fieldName);
        quoted(json, value);
    }

    private static void field(StringBuilder json, String fieldName, boolean value) {
        name(json, fieldName);
        json.append(value);
    }

    private static void field(StringBuilder json, String fieldName, int value) {
        name(json, fieldName);
        json.append(value);
    }

    private static void field(StringBuilder json, String fieldName, long value) {
        name(json, fieldName);
        json.append(value);
    }

    private static void field(StringBuilder json, String fieldName, float value) {
        name(json, fieldName);
        if (Float.isFinite(value)) {
            json.append(value);
        } else {
            json.append("null");
        }
    }

    private static void name(StringBuilder json, String fieldName) {
        char previous = json.charAt(json.length() - 1);
        if (previous != '{' && previous != '[') {
            json.append(',');
        }
        quoted(json, fieldName);
        json.append(':');
    }

    private static void quoted(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append("\\u00")
                                .append(Character.forDigit((character >> 4) & 0xf, 16))
                                .append(Character.forDigit(character & 0xf, 16));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private GeometryValidation() {}
}
