package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure lifecycle coverage for the native location boundary used by Mushroom patrol. */
public final class MushroomLocationControllerTest {
    private static final MapCoordinate LOCATION = new MapCoordinate(25.0330, 121.5650);

    @Test
    public void successfulLocationUpdateBecomesActiveAndStopIsIdempotent() {
        FakeDriver driver = new FakeDriver(true);
        MushroomLocationController controller = new MushroomLocationController(driver);

        assertTrue(controller.setLocation(LOCATION));
        assertEquals(MushroomLocationController.State.ACTIVE, controller.state());
        assertTrue(controller.mockLocationSent());
        assertEquals(LOCATION, controller.activeLocation());

        controller.stop();
        controller.stop();

        assertEquals(MushroomLocationController.State.IDLE, controller.state());
        assertFalse(controller.mockLocationSent());
        assertEquals(null, controller.activeLocation());
        assertEquals(1, driver.clearCalls);
    }

    @Test
    public void driverFailureDoesNotExposeActiveLocation() {
        FakeDriver driver = new FakeDriver(false);
        MushroomLocationController controller = new MushroomLocationController(driver);

        assertFalse(controller.setLocation(LOCATION));
        assertEquals(MushroomLocationController.State.ERROR, controller.state());
        assertFalse(controller.mockLocationSent());
        assertEquals(null, controller.activeLocation());
        assertTrue(controller.errorMessage().contains("rejected"));
    }

    @Test
    public void invalidLocationIsRejectedBeforeDriverCall() {
        FakeDriver driver = new FakeDriver(true);
        MushroomLocationController controller = new MushroomLocationController(driver);

        assertFalse(controller.setLocation(null));
        assertEquals(MushroomLocationController.State.ERROR, controller.state());
        assertEquals(0, driver.setCalls);
    }

    @Test
    public void providerInitializationFailureBlocksLocationUpdate() {
        FakeDriver driver = new FakeDriver(true);
        driver.initializationAccepted = false;
        MushroomLocationController controller = new MushroomLocationController(driver);

        assertFalse(controller.initialize());
        assertFalse(controller.setLocation(LOCATION));
        assertEquals(MushroomLocationController.State.ERROR, controller.state());
        assertEquals(0, driver.setCalls);
        assertTrue(controller.errorMessage().contains("initialization"));
    }

    @Test
    public void initializationLossClearsPreviouslySentLocation() {
        FakeDriver driver = new FakeDriver(true);
        MushroomLocationController controller = new MushroomLocationController(driver);

        assertTrue(controller.setLocation(LOCATION));
        driver.initializationAccepted = false;
        assertFalse(controller.setLocation(LOCATION));

        assertFalse(controller.mockLocationSent());
        assertEquals(null, controller.requestedLocation());
        assertEquals(null, controller.confirmedLocation());
    }

    @Test
    public void requestedLocationStaysUnconfirmedUntilDriverUpdate() {
        FakeDriver driver = new FakeDriver(true);
        MushroomLocationController controller = new MushroomLocationController(driver);

        assertTrue(controller.setLocation(LOCATION));
        assertTrue(controller.beginConfirmation((coordinate, accuracy, timestamp) ->
                controller.confirmLocation(coordinate, accuracy, timestamp)));
        assertEquals(MushroomLocationController.State.WAITING_CONFIRMATION, controller.state());
        assertEquals(null, controller.confirmedLocation());

        driver.emit(LOCATION, 1.0f, 200L);

        assertEquals(MushroomLocationController.State.CONFIRMED, controller.state());
        assertTrue(controller.mockLocationSent());
        assertEquals(LOCATION, controller.confirmedLocation());
        assertEquals(1.0f, controller.confirmedAccuracyMeters(), 0.0f);
        assertEquals(200L, controller.confirmedAtUptimeMillis());
    }

    @Test
    public void confirmationDriverFailureDoesNotAuthorizeLocation() {
        FakeDriver driver = new FakeDriver(true);
        driver.confirmationAccepted = false;
        MushroomLocationController controller = new MushroomLocationController(driver);

        assertTrue(controller.setLocation(LOCATION));
        assertFalse(controller.beginConfirmation((coordinate, accuracy, timestamp) -> {}));
        assertEquals(MushroomLocationController.State.ERROR, controller.state());
        assertTrue(controller.mockLocationSent());
        assertEquals(null, controller.confirmedLocation());
    }

    private static final class FakeDriver implements MushroomLocationController.Driver {
        private final boolean accepted;
        int setCalls;
        int clearCalls;
        boolean initializationAccepted = true;
        boolean confirmationAccepted = true;
        MushroomLocationController.LocationUpdateListener listener;

        FakeDriver(boolean accepted) {
            this.accepted = accepted;
        }

        @Override
        public boolean initialize() {
            return initializationAccepted;
        }

        @Override
        public boolean isInitialized() {
            return initializationAccepted;
        }

        @Override
        public boolean setLocation(MapCoordinate coordinate) {
            setCalls++;
            return accepted;
        }

        @Override
        public boolean requestLocationUpdates(
                MushroomLocationController.LocationUpdateListener nextListener) {
            listener = nextListener;
            return confirmationAccepted;
        }

        @Override
        public void cancelLocationUpdates() {
            listener = null;
        }

        @Override
        public void clearLocation() {
            clearCalls++;
        }

        void emit(MapCoordinate coordinate, float accuracy, long timestamp) {
            if (listener != null) {
                listener.onLocationUpdate(coordinate, accuracy, timestamp);
            }
        }
    }
}
