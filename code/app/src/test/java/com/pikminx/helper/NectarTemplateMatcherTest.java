package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public final class NectarTemplateMatcherTest {
    @Test
    public void mapsBaseAndFlowerTargetsToPackagedTemplateNames() {
        assertEquals("精華.png", NectarTemplateMatcher.assetNameFor("白色花瓣"));
        assertEquals("黃色精華.png", NectarTemplateMatcher.assetNameFor("黃色花瓣"));
        assertEquals("紅色扶桑花.png", NectarTemplateMatcher.assetNameFor("紅色扶桑花"));
    }

    @Test
    public void acceptsExpectedOrNearBestVisualEvidence() {
        assertEquals(
                NectarTemplateMatcher.Status.MATCHED,
                NectarTemplateMatcher.classify(true, 0.70f, 0.74f, false));
        assertEquals(
                NectarTemplateMatcher.Status.MATCHED,
                NectarTemplateMatcher.classify(true, 0.70f, 0.70f, true));
    }

    @Test
    public void rejectsClearConflictButKeepsUncertainImageAsUnknown() {
        assertEquals(
                NectarTemplateMatcher.Status.CONFLICT,
                NectarTemplateMatcher.classify(true, 0.50f, 0.80f, false));
        assertEquals(
                NectarTemplateMatcher.Status.UNKNOWN,
                NectarTemplateMatcher.classify(true, 0.55f, 0.60f, false));
    }

    @Test
    public void missingTemplateFallsBackToNormalOcrStability() {
        assertEquals(
                NectarTemplateMatcher.Status.UNAVAILABLE,
                NectarTemplateMatcher.classify(false, 0f, 0f, false));
        assertEquals(2, NectarTemplateMatcher.requiredStableFrames(
                NectarTemplateMatcher.Status.UNAVAILABLE, 2));
        assertEquals(3, NectarTemplateMatcher.requiredStableFrames(
                NectarTemplateMatcher.Status.UNKNOWN, 2));
    }

    @Test
    public void reusesOnlyTheSameCandidateAndAdjacentFrameSignature() {
        NectarTemplateMatchCache cache = new NectarTemplateMatchCache();
        NectarTemplateMatchCache.Key first = key(1L, 10L, "SELECTING_NECTAR",
                "白色花瓣#80#900", 101L);
        NectarTemplateMatcher.Evidence evidence = new NectarTemplateMatcher.Evidence(
                NectarTemplateMatcher.Status.MATCHED, 0.8f, 0.82f);
        cache.put(first, evidence, 1_000L);

        assertSame(evidence, cache.getIfReusable(key(1L, 10L, "SELECTING_NECTAR",
                "白色花瓣#80#900", 101L), 1_100L));
        assertSame(evidence, cache.getIfReusable(key(1L, 11L, "SELECTING_NECTAR",
                "白色花瓣#80#900", 101L), 1_200L));
        assertNull(cache.getIfReusable(key(1L, 12L, "SELECTING_NECTAR",
                "白色花瓣#80#900", 101L), 1_300L));
    }

    @Test
    public void fallsBackToFullMatchingWhenCandidateFrameOrStateChanges() {
        NectarTemplateMatchCache cache = new NectarTemplateMatchCache();
        NectarTemplateMatcher.Evidence evidence = new NectarTemplateMatcher.Evidence(
                NectarTemplateMatcher.Status.UNKNOWN, 0.55f, 0.60f);
        cache.put(key(3L, 20L, "SELECTING_NECTAR", "白色花瓣#80#900", 201L),
                evidence, 2_000L);

        assertNull(cache.getIfReusable(
                key(3L, 21L, "SELECTING_NECTAR", "黃色花瓣#80#900", 201L), 2_100L));
        assertNull(cache.getIfReusable(
                key(3L, 21L, "SELECTING_NECTAR", "白色花瓣#80#901", 201L), 2_100L));
        assertNull(cache.getIfReusable(
                key(3L, 21L, "WAITING_NECTAR_PANEL_CLOSE", "白色花瓣#80#900", 201L), 2_100L));
        assertNull(cache.getIfReusable(
                key(4L, 21L, "SELECTING_NECTAR", "白色花瓣#80#900", 201L), 2_100L));
        assertNull(cache.getIfReusable(
                key(3L, 21L, "SELECTING_NECTAR", "白色花瓣#80#900", 202L), 2_100L));
        assertNull(cache.getIfReusable(
                key(3L, 21L, "SELECTING_NECTAR", "白色花瓣#80#900", 201L), 3_501L));
    }

    private static NectarTemplateMatchCache.Key key(
            long generation,
            long captureSequence,
            String state,
            String candidate,
            long signature) {
        return new NectarTemplateMatchCache.Key(
                generation,
                captureSequence,
                state,
                candidate,
                "精華.png",
                540,
                800,
                1080,
                2400,
                205,
                new CaptureGeometry.Bounds(40, 100, 1120, 2500),
                12,
                signature);
    }
}
