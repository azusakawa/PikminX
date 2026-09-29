package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class MapSceneInventoryTest {
    private static final int WIDTH = 432;
    private static final int HEIGHT = 936;

    @Test
    public void countsFourFlowersAndThreeMushroomsFromConfirmedMapObjects() {
        List<MapSceneInventory.ObjectMarker> markers = List.of(
                marker(MapSceneInventory.Kind.MUSHROOM, 95, 180),
                marker(MapSceneInventory.Kind.MUSHROOM, 45, 250),
                marker(MapSceneInventory.Kind.MUSHROOM, 385, 315),
                marker(MapSceneInventory.Kind.BIG_FLOWER, 140, 215),
                marker(MapSceneInventory.Kind.BIG_FLOWER, 335, 255),
                marker(MapSceneInventory.Kind.BIG_FLOWER, 345, 330),
                marker(MapSceneInventory.Kind.BIG_FLOWER, 245, 340));

        MapSceneInventory.Count count = MapSceneInventory.count(markers);

        assertEquals(4, count.bigFlowers());
        assertEquals(3, count.mushrooms());
    }

    @Test
    public void onlyConfirmedBigFlowersAreEligibleForTapping() {
        assertTrue(MapSceneInventory.isSafeFlowerCandidate(
                marker(MapSceneInventory.Kind.BIG_FLOWER, 245, 340), WIDTH, HEIGHT));
        assertFalse(MapSceneInventory.isSafeFlowerCandidate(
                marker(MapSceneInventory.Kind.MUSHROOM, 385, 315), WIDTH, HEIGHT));
        assertFalse(MapSceneInventory.isSafeFlowerCandidate(
                marker(MapSceneInventory.Kind.PLAYER, 215, 470), WIDTH, HEIGHT));
        assertFalse(MapSceneInventory.isSafeFlowerCandidate(
                marker(MapSceneInventory.Kind.OVERLAY, 25, 120), WIDTH, HEIGHT));
    }

    private static MapSceneInventory.ObjectMarker marker(
            MapSceneInventory.Kind kind, int x, int y) {
        return new MapSceneInventory.ObjectMarker(kind, x, y);
    }
}
