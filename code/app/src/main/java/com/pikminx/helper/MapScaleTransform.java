package com.pikminx.helper;

/** Converts detector coordinates between the current device bitmap and normalized analysis size. */
final class MapScaleTransform {
    record Size(int width, int height) {}

    private static final int MAX_ANALYSIS_WIDTH = 432;

    private MapScaleTransform() {}

    static Size analysisSize(int sourceWidth, int sourceHeight) {
        int width = Math.min(MAX_ANALYSIS_WIDTH, Math.max(1, sourceWidth));
        int height = Math.max(
                1,
                Math.round(sourceHeight * (width / (float) Math.max(1, sourceWidth))));
        return new Size(width, height);
    }

    static MapFlowerDetector.Point toSource(
            MapFlowerDetector.Point point,
            int sourceWidth,
            int sourceHeight,
            int analysisWidth,
            int analysisHeight) {
        float scaleX = sourceWidth / (float) Math.max(1, analysisWidth);
        float scaleY = sourceHeight / (float) Math.max(1, analysisHeight);
        return new MapFlowerDetector.Point(
                Math.round(point.x() * scaleX),
                Math.round(point.y() * scaleY),
                point.area(),
                point.visualKind());
    }
}
