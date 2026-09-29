package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MapScaleTransformTest {
    @Test
    public void derivesAnalysisSizeFromCurrentDeviceBitmap() {
        assertEquals(new MapScaleTransform.Size(432, 960),
                MapScaleTransform.analysisSize(1080, 2400));
        assertEquals(new MapScaleTransform.Size(432, 936),
                MapScaleTransform.analysisSize(720, 1560));
        assertEquals(new MapScaleTransform.Size(360, 800),
                MapScaleTransform.analysisSize(360, 800));
    }

    @Test
    public void mapsRelativeDetectorPointBackToActualDeviceCoordinates() {
        MapFlowerDetector.Point source = MapScaleTransform.toSource(
                new MapFlowerDetector.Point(
                        246, 337, 50, MapFlowerDetector.VisualKind.WHITE),
                1080,
                2400,
                432,
                960);

        assertEquals(615, source.x());
        assertEquals(843, source.y());
    }
}
