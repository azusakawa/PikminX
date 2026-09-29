package com.pikminx.helper;

/** Bounded, short-lived cache for visual evidence from one stable nectar card. */
final class NectarTemplateMatchCache {
    static final long MAX_CACHE_AGE_MILLIS = 1_500L;
    private static final long MAX_CAPTURE_SEQUENCE_GAP = 1L;

    record Key(
            long runGeneration,
            long captureSequence,
            String workflowState,
            String candidateIdentity,
            String expectedAsset,
            int centerX,
            int centerY,
            int screenWidth,
            int screenHeight,
            int side,
            CaptureGeometry.Bounds windowBounds,
            int windowId,
            long frameSignature) {}

    private record Entry(Key key, NectarTemplateMatcher.Evidence evidence, long storedAtUptimeMillis) {}

    private Entry entry;
    private long cacheHitCount;

    synchronized NectarTemplateMatcher.Evidence getIfReusable(Key requested, long nowUptimeMillis) {
        if (!isValid(requested) || nowUptimeMillis < 0L) {
            return null;
        }
        Entry current = entry;
        if (current == null) {
            return null;
        }
        if (nowUptimeMillis < current.storedAtUptimeMillis
                || nowUptimeMillis - current.storedAtUptimeMillis > MAX_CACHE_AGE_MILLIS) {
            entry = null;
            return null;
        }
        long sequenceGap = requested.captureSequence() - current.key().captureSequence();
        if (sequenceGap < 0L || sequenceGap > MAX_CAPTURE_SEQUENCE_GAP
                || !sameRelevantState(current.key(), requested)) {
            return null;
        }
        cacheHitCount++;
        return current.evidence();
    }

    synchronized void put(
            Key key, NectarTemplateMatcher.Evidence evidence, long storedAtUptimeMillis) {
        if (!isValid(key) || evidence == null || storedAtUptimeMillis < 0L) {
            return;
        }
        entry = new Entry(key, evidence, storedAtUptimeMillis);
    }

    synchronized void clear() {
        entry = null;
    }

    synchronized long cacheHitCount() {
        return cacheHitCount;
    }

    private static boolean sameRelevantState(Key first, Key second) {
        return first.runGeneration() == second.runGeneration()
                && java.util.Objects.equals(first.workflowState(), second.workflowState())
                && java.util.Objects.equals(first.candidateIdentity(), second.candidateIdentity())
                && java.util.Objects.equals(first.expectedAsset(), second.expectedAsset())
                && first.centerX() == second.centerX()
                && first.centerY() == second.centerY()
                && first.screenWidth() == second.screenWidth()
                && first.screenHeight() == second.screenHeight()
                && first.side() == second.side()
                && java.util.Objects.equals(first.windowBounds(), second.windowBounds())
                && first.windowId() == second.windowId()
                && first.frameSignature() == second.frameSignature();
    }

    private static boolean isValid(Key key) {
        return key != null
                && key.runGeneration() >= 1L
                && key.captureSequence() >= 1L
                && key.workflowState() != null
                && !key.workflowState().isBlank()
                && key.candidateIdentity() != null
                && !key.candidateIdentity().isBlank()
                && key.expectedAsset() != null
                && !key.expectedAsset().isBlank()
                && key.screenWidth() > 0
                && key.screenHeight() > 0
                && key.side() > 0
                && key.windowBounds() != null;
    }
}
