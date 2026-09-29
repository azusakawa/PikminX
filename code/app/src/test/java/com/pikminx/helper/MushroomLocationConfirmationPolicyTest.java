package com.pikminx.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure distance-policy coverage for the mock-location confirmation gate. */
public final class MushroomLocationConfirmationPolicyTest {
    private static final MapCoordinate TARGET = new MapCoordinate(25.0330, 121.5650);

    @Test
    public void coordinateWithinToleranceIsAccepted() {
        MapCoordinate confirmed = new MapCoordinate(25.0333, 121.5650);

        assertTrue(MushroomLocationConfirmationPolicy.isWithinTolerance(
                TARGET, confirmed, MushroomLocationConfirmationPolicy.DEFAULT_TOLERANCE_METERS));
    }

    @Test
    public void coordinateOutsideToleranceIsRejected() {
        MapCoordinate confirmed = new MapCoordinate(25.0350, 121.5650);

        assertFalse(MushroomLocationConfirmationPolicy.isWithinTolerance(
                TARGET, confirmed, MushroomLocationConfirmationPolicy.DEFAULT_TOLERANCE_METERS));
    }

    @Test
    public void invalidToleranceAndMissingCoordinatesAreRejected() {
        assertFalse(MushroomLocationConfirmationPolicy.isWithinTolerance(TARGET, null, 75.0));
        assertFalse(MushroomLocationConfirmationPolicy.isWithinTolerance(TARGET, TARGET, -1.0));
    }
}
