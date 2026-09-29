package com.pikminx.helper;

record PlantingControlEvidence(
        PlantingScreenAnalyzer.Detection detection,
        PetalMatcher.Token ocrStartControl,
        boolean accessibilityStartVisible,
        boolean accessibilityStopVisible) {

    PlantingScreenAnalyzer.Point visualStartControl() {
        return detection.startControl();
    }

    PlantingScreenAnalyzer.Point visualStopControl() {
        return detection.stopControl();
    }

    boolean startVisible() {
        return visualStartControl() != null
                || ocrStartControl != null
                || accessibilityStartVisible;
    }

    boolean stopVisible() {
        return visualStopControl() != null || accessibilityStopVisible;
    }
}
