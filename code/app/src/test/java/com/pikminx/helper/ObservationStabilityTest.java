package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ObservationStabilityTest {
    @Test
    public void requiresTwoMatchingSemanticFrames() {
        ObservationStability gate = gate();

        assertEquals(ObservationStability.Result.CANDIDATE,
                gate.observe("白色花瓣:120", 180, 900, 720, 1600));
        assertEquals(ObservationStability.Result.STABLE,
                gate.observe("白色花瓣:120", 184, 906, 720, 1600));
    }

    @Test
    public void semanticConflictRestartsConfirmation() {
        ObservationStability gate = gate();
        gate.observe("白色花瓣:120", 180, 900, 720, 1600);

        assertEquals(ObservationStability.Result.CANDIDATE,
                gate.observe("白色花瓣:20", 180, 900, 720, 1600));
        assertEquals(1, gate.confirmations());
    }

    @Test
    public void toleratesTwoMissingFramesThenDropsStaleCandidate() {
        ObservationStability gate = gate();
        gate.observe("白色花瓣:120", 180, 900, 720, 1600);

        gate.miss();
        gate.miss();
        assertTrue(gate.hasCandidate());
        gate.miss();
        assertFalse(gate.hasCandidate());
    }

    @Test
    public void confirmsMatchingEvidenceAcrossOneMissingOcrFrame() {
        ObservationStability gate = new ObservationStability(2, 1, 0.01f, 0.01f);

        assertEquals(ObservationStability.Result.CANDIDATE,
                gate.observe("planting-active-map", 360, 800, 720, 1600));
        gate.miss();
        assertEquals(ObservationStability.Result.STABLE,
                gate.observe("planting-active-map", 360, 800, 720, 1600));
    }

    @Test
    public void resetRequiresTheEntryToBecomeStableAgainBeforeRetry() {
        ObservationStability gate = new ObservationStability(2, 1, 0.025f, 0.025f);
        gate.observe("planting-map-entry", 620, 1200, 720, 1600);
        assertEquals(ObservationStability.Result.STABLE,
                gate.observe("planting-map-entry", 622, 1204, 720, 1600));

        gate.reset();

        assertEquals(ObservationStability.Result.CANDIDATE,
                gate.observe("planting-map-entry", 622, 1204, 720, 1600));
    }

    @Test
    public void largeRelativeEntryDriftRestartsConfirmation() {
        ObservationStability gate = new ObservationStability(2, 1, 0.025f, 0.025f);

        assertEquals(ObservationStability.Result.CANDIDATE,
                gate.observe("planting-map-entry", 1185, 1917, 1280, 2772));
        assertEquals(ObservationStability.Result.CANDIDATE,
                gate.observe("planting-map-entry", 1085, 1894, 1280, 2772));
        assertEquals(1, gate.confirmations());
    }

    private static ObservationStability gate() {
        return new ObservationStability(2, 2, 0.08f, 0.07f);
    }
}
