package com.pikminx.helper;

import java.util.ArrayList;
import java.util.List;

/** Shared OCR profile, coordinate transform and per-frame result. */
final class OcrScan {
    private static final int WHITE = 0xFFFFFFFF;

    enum ScriptMode { MULTILINGUAL, CHINESE }
    enum RoiBasis { SCREENSHOT, TARGET_WINDOW }

    enum Profile {
        FULL_MULTILINGUAL(ScriptMode.MULTILINGUAL, RoiBasis.SCREENSHOT,
                0f, 0f, 1f, 1f, 1080, 1440),
        FULL_CHINESE(ScriptMode.CHINESE, RoiBasis.SCREENSHOT,
                0f, 0f, 1f, 1f, 1080, 1440),
        DISPATCH_LIST(ScriptMode.CHINESE, RoiBasis.TARGET_WINDOW,
                0f, 0.18f, 1f, 0.90f, 1440, 2160),
        PETAL_LIST(ScriptMode.CHINESE, RoiBasis.TARGET_WINDOW,
                0f, 0.44f, 1f, 0.96f, 1440, 2160);

        private final ScriptMode scriptMode;
        private final RoiBasis roiBasis;
        private final float leftFraction;
        private final float topFraction;
        private final float rightFraction;
        private final float bottomFraction;
        private final int minimumAnalysisWidth;
        private final int maximumAnalysisWidth;

        Profile(
                ScriptMode scriptMode,
                RoiBasis roiBasis,
                float leftFraction,
                float topFraction,
                float rightFraction,
                float bottomFraction,
                int minimumAnalysisWidth,
                int maximumAnalysisWidth) {
            this.scriptMode = scriptMode;
            this.roiBasis = roiBasis;
            this.leftFraction = leftFraction;
            this.topFraction = topFraction;
            this.rightFraction = rightFraction;
            this.bottomFraction = bottomFraction;
            this.minimumAnalysisWidth = minimumAnalysisWidth;
            this.maximumAnalysisWidth = maximumAnalysisWidth;
        }

        ScriptMode scriptMode() { return scriptMode; }
        RoiBasis roiBasis() { return roiBasis; }
    }

    interface PixelReader {
        int get(int x, int y);
    }

    record Transform(
            int sourceWidth,
            int sourceHeight,
            int cropLeft,
            int cropTop,
            int cropWidth,
            int cropHeight,
            int analysisWidth,
            int analysisHeight,
            RoiBasis roiBasis,
            int basisLeft,
            int basisTop,
            int basisRight,
            int basisBottom,
            boolean fallbackToScreenshot,
            String fallbackReason) {

        static Transform create(Profile profile, int sourceWidth, int sourceHeight) {
            return create(profile, sourceWidth, sourceHeight, null);
        }

        static Transform create(
                Profile profile,
                int sourceWidth,
                int sourceHeight,
                CaptureGeometry captureGeometry) {
            int safeWidth = Math.max(1, sourceWidth);
            int safeHeight = Math.max(1, sourceHeight);
            ScreenCoordinateTransform.ScreenshotRect basis =
                    new ScreenCoordinateTransform.ScreenshotRect(0, 0, safeWidth, safeHeight);
            boolean fallbackToScreenshot = false;
            String fallbackReason = "";
            if (profile.roiBasis == RoiBasis.TARGET_WINDOW) {
                ScreenCoordinateTransform.ScreenshotRect target = captureGeometry == null
                        ? null : ScreenCoordinateTransform.targetWindowInScreenshot(captureGeometry);
                if (target == null) {
                    fallbackToScreenshot = true;
                    fallbackReason = captureGeometry == null
                            ? "target_geometry_missing" : "target_window_unresolved";
                } else {
                    basis = target;
                }
            }
            int left = clamp(
                    basis.left() + Math.round(basis.width() * profile.leftFraction),
                    basis.left(), basis.right() - 1);
            int top = clamp(
                    basis.top() + Math.round(basis.height() * profile.topFraction),
                    basis.top(), basis.bottom() - 1);
            int right = clamp(
                    basis.left() + Math.round(basis.width() * profile.rightFraction),
                    left + 1, basis.right());
            int bottom = clamp(
                    basis.top() + Math.round(basis.height() * profile.bottomFraction),
                    top + 1, basis.bottom());
            int cropWidth = right - left;
            int cropHeight = bottom - top;
            int targetWidth = profile.minimumAnalysisWidth == 0
                    ? cropWidth
                    : clamp(
                            cropWidth,
                            profile.minimumAnalysisWidth,
                            profile.maximumAnalysisWidth);
            int targetHeight = Math.max(
                    1,
                    Math.round(cropHeight * (targetWidth / (float) cropWidth)));
            return new Transform(
                    safeWidth,
                    safeHeight,
                    left,
                    top,
                    cropWidth,
                    cropHeight,
                    targetWidth,
                    targetHeight,
                    profile.roiBasis,
                    basis.left(),
                    basis.top(),
                    basis.right(),
                    basis.bottom(),
                    fallbackToScreenshot,
                    fallbackReason);
        }

        boolean usesSourceBitmap() {
            return cropLeft == 0
                    && cropTop == 0
                    && cropWidth == sourceWidth
                    && cropHeight == sourceHeight
                    && analysisWidth == sourceWidth
                    && analysisHeight == sourceHeight;
        }

        List<PetalMatcher.Token> toSourceTokens(List<PetalMatcher.Token> tokens) {
            if (usesSourceBitmap()) {
                return List.copyOf(tokens);
            }
            List<PetalMatcher.Token> mapped = new ArrayList<>(tokens.size());
            for (PetalMatcher.Token token : tokens) {
                mapped.add(new PetalMatcher.Token(
                        token.text(),
                        sourceX(token.left()),
                        sourceY(token.top()),
                        sourceX(token.right()),
                        sourceY(token.bottom())));
            }
            return List.copyOf(mapped);
        }

        int sourceX(int analysisX) {
            return clamp(
                    cropLeft + Math.round(analysisX * (cropWidth / (float) analysisWidth)),
                    cropLeft,
                    cropLeft + cropWidth);
        }

        int sourceY(int analysisY) {
            return clamp(
                    cropTop + Math.round(analysisY * (cropHeight / (float) analysisHeight)),
                    cropTop,
                    cropTop + cropHeight);
        }

        int analysisX(int sourceX) {
            return clamp(
                    Math.round((sourceX - cropLeft) * (analysisWidth / (float) cropWidth)),
                    0,
                    analysisWidth - 1);
        }

        int analysisY(int sourceY) {
            return clamp(
                    Math.round((sourceY - cropTop) * (analysisHeight / (float) cropHeight)),
                    0,
                    analysisHeight - 1);
        }

        boolean containsSource(int x, int y) {
            return x >= cropLeft
                    && x < cropLeft + cropWidth
                    && y >= cropTop
                    && y < cropTop + cropHeight;
        }
    }

    record Frame(
            Profile profile,
            Transform transform,
            List<PetalMatcher.Token> tokens,
            long elapsedMillis,
            PixelReader analysisPixels,
            CaptureGeometry captureGeometry) {

        Frame {
            tokens = List.copyOf(tokens);
            if (captureGeometry == null) {
                throw new IllegalArgumentException("Capture geometry is required");
            }
        }

        int sourceWidth() { return transform.sourceWidth(); }
        int sourceHeight() { return transform.sourceHeight(); }

        int pixelAtSource(int x, int y) {
            if (analysisPixels == null || !transform.containsSource(x, y)) {
                return WHITE;
            }
            return analysisPixels.get(transform.analysisX(x), transform.analysisY(y));
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private OcrScan() {}
}
