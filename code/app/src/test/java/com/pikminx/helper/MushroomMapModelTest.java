package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Pure coverage for Mushroom map selections and deterministic patrol routes. */
public final class MushroomMapModelTest {
    private static final MapCoordinate A = new MapCoordinate(25.0000, 121.0000);
    private static final MapCoordinate B = new MapCoordinate(25.0100, 121.0200);

    @Test
    public void pointSelectionProducesOnePoint() {
        MapSelection selection = MapSelection.point(A);

        List<PatrolPoint> points = PatrolRouteGenerator.generate(selection, 500.0);

        assertEquals(MapSelection.Mode.POINT, selection.mode());
        assertEquals(1, points.size());
        assertEquals(A, points.get(0).coordinate());
    }

    @Test
    public void twoPointForwardReverseAndRoundTripPreserveDirection() {
        List<PatrolPoint> forward = PatrolRouteGenerator.generate(
                MapSelection.twoPoint(A, B, MapSelection.Direction.FORWARD), 500.0);
        List<PatrolPoint> reverse = PatrolRouteGenerator.generate(
                MapSelection.twoPoint(A, B, MapSelection.Direction.REVERSE), 500.0);
        List<PatrolPoint> roundTrip = PatrolRouteGenerator.generate(
                MapSelection.twoPoint(A, B, MapSelection.Direction.ROUND_TRIP), 500.0);

        assertEquals(A, forward.get(0).coordinate());
        assertEquals(B, forward.get(forward.size() - 1).coordinate());
        assertEquals(B, reverse.get(0).coordinate());
        assertEquals(A, reverse.get(reverse.size() - 1).coordinate());
        assertEquals(A, roundTrip.get(0).coordinate());
        assertEquals(A, roundTrip.get(roundTrip.size() - 1).coordinate());
        assertTrue(roundTrip.size() > forward.size());
    }

    @Test
    public void rectangleUsesDeterministicSerpentinePointsInsideBounds() {
        MapSelection selection = MapSelection.rectangle(A, B);

        List<PatrolPoint> first = PatrolRouteGenerator.generate(selection, 500.0);
        List<PatrolPoint> second = PatrolRouteGenerator.generate(selection, 500.0);

        assertEquals(first, second);
        assertTrue(first.size() > 1);
        for (PatrolPoint point : first) {
            assertTrue(point.coordinate().latitude() >= A.latitude());
            assertTrue(point.coordinate().latitude() <= B.latitude());
            assertTrue(point.coordinate().longitude() >= A.longitude());
            assertTrue(point.coordinate().longitude() <= B.longitude());
        }
        assertFalse(hasDuplicateCoordinates(first));
    }

    @Test
    public void circleNeverGeneratesOutsideSelectedGeometry() {
        MapCoordinate center = new MapCoordinate(25.0330, 121.5650);
        List<PatrolPoint> points = PatrolRouteGenerator.generate(
                MapSelection.circle(center, 1200.0), 400.0);

        assertFalse(points.isEmpty());
        for (PatrolPoint point : points) {
            assertTrue(center.distanceMeters(point.coordinate()) <= 1200.1);
        }
    }

    @Test
    public void routeSelectionKeepsUserWaypointOrder() {
        List<MapCoordinate> waypoints = List.of(A, B, new MapCoordinate(25.0200, 121.0100));

        List<PatrolPoint> points = PatrolRouteGenerator.generate(
                MapSelection.route(waypoints), 500.0);

        assertEquals(waypoints, points.stream().map(PatrolPoint::coordinate).toList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidLatitudeIsRejected() {
        new MapCoordinate(91.0, 121.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSpacingIsRejected() {
        PatrolRouteGenerator.generate(MapSelection.point(A), 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversizedTwoPointRouteIsRejectedBeforeAllocation() {
        PatrolRouteGenerator.generate(
                MapSelection.twoPoint(
                        new MapCoordinate(-80.0, -170.0),
                        new MapCoordinate(80.0, 170.0),
                        MapSelection.Direction.FORWARD),
                1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversizedAreaGridIsRejectedBeforeAllocation() {
        PatrolRouteGenerator.generate(
                MapSelection.rectangle(new MapCoordinate(0.0, 0.0),
                        new MapCoordinate(1.0, 1.0)),
                1.0);
    }

    private static boolean hasDuplicateCoordinates(List<PatrolPoint> points) {
        List<MapCoordinate> seen = new ArrayList<>();
        for (PatrolPoint point : points) {
            if (seen.contains(point.coordinate())) {
                return true;
            }
            seen.add(point.coordinate());
        }
        return false;
    }
}
