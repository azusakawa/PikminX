package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** Pure tests preventing screen pixels or patrol points from becoming fake Mushroom coordinates. */
public final class MushroomCoordinateIntegrityTest {
    private static final MapCoordinate SCAN = new MapCoordinate(25.0330, 121.5650);
    private static final MapCoordinate SELECTED = new MapCoordinate(25.0340, 121.5660);

    @Test
    public void detectorResultStartsWithoutJumpableGeographicCoordinate() {
        MushroomUiState.Result result = result();

        assertEquals(MushroomCoordinateSource.UNAVAILABLE,
                result.mushroomCoordinateSource());
        assertNull(result.mushroomCoordinate());
        assertEquals(SCAN, result.scanLocation());
        assertFalse(result.canJumpToMushroom());
        assertEquals(717, result.detectionScreenX());
        assertEquals(663, result.detectionScreenY());
        assertEquals(42L, result.captureSequence());
    }

    @Test
    public void onlyUserSelectedAndAuthoritativeCoordinatesCanJump() {
        MushroomUiState.Result userSelected = result().withUserSelectedCoordinate(SELECTED);
        MushroomUiState.Result authoritative = new MushroomUiState.Result(
                result().detectionId(),
                result().type(),
                result().size(),
                result().confidence(),
                result().screenX(),
                result().screenY(),
                result().detectionBounds(),
                result().scanLocation(),
                result().scanAccuracy(),
                result().scanConfirmedAtUptimeMillis(),
                SELECTED,
                MushroomCoordinateSource.AUTHORITATIVE,
                -1.0);
        MushroomUiState.Result estimated = new MushroomUiState.Result(
                result().detectionId(),
                result().type(),
                result().size(),
                result().confidence(),
                result().screenX(),
                result().screenY(),
                result().detectionBounds(),
                result().scanLocation(),
                result().scanAccuracy(),
                result().scanConfirmedAtUptimeMillis(),
                SELECTED,
                MushroomCoordinateSource.CALIBRATED_ESTIMATE,
                100.0);

        assertTrue(userSelected.canJumpToMushroom());
        assertTrue(authoritative.canJumpToMushroom());
        assertFalse(estimated.canJumpToMushroom());
    }

    @Test
    public void assigningUiCoordinatePreservesDetectionAndScanEvidence() {
        MushroomUiState state = MushroomUiState.initial().withScan(
                MushroomUiState.ScanStatus.RESULTS,
                "found",
                9L,
                List.of(result()));

        MushroomUiState assigned = state.withMushroomCoordinate(
                result().detectionId(), SELECTED);
        MushroomUiState.Result value = assigned.results().get(0);

        assertNotSame(state, assigned);
        assertEquals(SELECTED, value.mushroomCoordinate());
        assertEquals(MushroomCoordinateSource.USER_SELECTED,
                value.mushroomCoordinateSource());
        assertEquals(SCAN, value.scanLocation());
        assertEquals(result().detectionBounds(), value.detectionBounds());
        assertEquals(42L, value.captureSequence());
        assertTrue(value.canJumpToMushroom());
        assertTrue(state.withMushroomCoordinate(
                new MushroomDetectionId(9L, 42L, 99), SELECTED) == state);
    }

    @Test
    public void patrolObservationKeepsPatrolPointAsTargetAndScanLocation() {
        MushroomObservation observation = new MushroomObservation(
                77L,
                0,
                SCAN,
                SCAN,
                4.0f,
                1234L,
                new MushroomDetectionId(9L, 42L, 0),
                717,
                663,
                new MushroomScreenBounds(700, 650, 730, 680),
                null,
                MushroomCoordinateSource.UNAVAILABLE,
                -1.0,
                "一般灰色蘑菇",
                "小型",
                0.56f,
                42L,
                2000L);

        assertEquals(SCAN, observation.targetLocation());
        assertEquals(SCAN, observation.scanLocation());
        assertNull(observation.mushroomCoordinate());
        assertFalse(observation.canJumpToMushroom());

        MushroomObservation assigned = observation.withUserSelectedCoordinate(SELECTED);
        assertEquals(SCAN, assigned.scanLocation());
        assertEquals(SELECTED, assigned.mushroomCoordinate());
        assertTrue(assigned.canJumpToMushroom());
    }

    @Test
    public void sourceCannotClaimCoordinateWhenCoordinateIsMissing() {
        try {
            new MushroomUiState.Result(
                    result().detectionId(),
                    result().type(),
                    result().size(),
                    result().confidence(),
                    result().screenX(),
                    result().screenY(),
                    result().detectionBounds(),
                    result().scanLocation(),
                    result().scanAccuracy(),
                    result().scanConfirmedAtUptimeMillis(),
                    null,
                    MushroomCoordinateSource.USER_SELECTED,
                    -1.0);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("A coordinate source must not exist without a coordinate");
    }

    private static MushroomUiState.Result result() {
        return new MushroomUiState.Result(
                new MushroomDetectionId(9L, 42L, 0),
                "一般灰色蘑菇",
                "小型",
                0.56f,
                717,
                663,
                new MushroomScreenBounds(700, 650, 730, 680),
                SCAN,
                4.0f,
                1234L,
                null,
                MushroomCoordinateSource.UNAVAILABLE,
                -1.0);
    }
}
