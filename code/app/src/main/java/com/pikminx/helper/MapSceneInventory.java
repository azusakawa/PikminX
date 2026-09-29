package com.pikminx.helper;

import java.util.ArrayList;
import java.util.List;

/**
 * A conservative inventory of objects visible on the map.
 *
 * <p>The inventory deliberately keeps unknown objects separate from confirmed big flowers.  An
 * unknown marker must never become a tap target merely because its pixels look flower-coloured.
 */
final class MapSceneInventory {
    enum Kind {
        BIG_FLOWER,
        MUSHROOM,
        PLAYER,
        OVERLAY,
        UNKNOWN
    }

    record ObjectMarker(Kind kind, int x, int y) {}
    record Count(int bigFlowers, int mushrooms) {}

    private final List<ObjectMarker> markers = new ArrayList<>();

    static Count count(List<ObjectMarker> markers) {
        int flowers = 0;
        int mushrooms = 0;
        for (ObjectMarker marker : markers) {
            if (marker.kind() == Kind.BIG_FLOWER) {
                flowers++;
            } else if (marker.kind() == Kind.MUSHROOM) {
                mushrooms++;
            }
        }
        return new Count(flowers, mushrooms);
    }

    static boolean isSafeFlowerCandidate(ObjectMarker marker, int width, int height) {
        return marker.kind() == Kind.BIG_FLOWER
                && marker.x() > width * 0.08f
                && marker.x() < width * 0.92f
                && marker.y() > height * 0.14f
                && marker.y() < height * 0.78f;
    }

    void mark(Kind kind, int x, int y, int mergeDistance) {
        markers.removeIf(marker -> Math.abs(marker.x() - x) < mergeDistance
                && Math.abs(marker.y() - y) < mergeDistance);
        markers.add(new ObjectMarker(kind, x, y));
    }

    Count count() {
        return count(markers);
    }

    int rejectedCount() {
        return (int) markers.stream()
                .filter(marker -> marker.kind() == Kind.MUSHROOM
                        || marker.kind() == Kind.PLAYER
                        || marker.kind() == Kind.OVERLAY
                        || marker.kind() == Kind.UNKNOWN)
                .count();
    }

    void clear() {
        markers.clear();
    }
}
