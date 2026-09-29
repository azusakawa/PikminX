package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MushroomDetectorPolicyTest {
    @Test
    public void v129AppearanceEvidenceKeepsEachAcceptedPath() {
        assertTrue(MushroomDetector.passesAppearanceEvidence(true, false, false, false));
        assertTrue(MushroomDetector.passesAppearanceEvidence(false, true, false, false));
        assertTrue(MushroomDetector.passesAppearanceEvidence(false, false, true, false));
        assertTrue(MushroomDetector.passesAppearanceEvidence(false, false, false, true));
    }

    @Test
    public void v129AppearanceEvidenceRejectsNoEvidence() {
        assertFalse(MushroomDetector.passesAppearanceEvidence(false, false, false, false));
    }

    @Test
    public void v129BlueEvidenceUsesTheReferenceMidValueRange() {
        assertTrue(MushroomDetector.isBlueOrDarkEvidence(180f, 0.40f, 0.18f));
        assertTrue(MushroomDetector.isBlueOrDarkEvidence(180f, 0.40f, 0.72f));
        assertFalse(MushroomDetector.isBlueOrDarkEvidence(180f, 0.40f, 0.17f));
        assertFalse(MushroomDetector.isBlueOrDarkEvidence(180f, 0.40f, 0.73f));
    }

    @Test
    public void v129DarkEvidenceUsesTheReferenceNeutralRange() {
        assertTrue(MushroomDetector.isBlueOrDarkEvidence(0f, 0.34f, 0.18f));
        assertTrue(MushroomDetector.isBlueOrDarkEvidence(0f, 0.34f, 0.55f));
        assertFalse(MushroomDetector.isBlueOrDarkEvidence(0f, 0.34f, 0.17f));
        assertFalse(MushroomDetector.isBlueOrDarkEvidence(0f, 0.34f, 0.56f));
        assertFalse(MushroomDetector.isBlueOrDarkEvidence(0f, 0.35f, 0.30f));
    }

    @Test
    public void fixedCatalogMismatchUsesEventClassification() {
        assertEquals("一般灰色蘑菇", MushroomDetector.classifyTemplateMatch("一般灰色蘑菇"));
        assertEquals("活動蘑菇", MushroomDetector.classifyTemplateMatch(null));
        assertEquals("活動蘑菇", MushroomDetector.classifyTemplateMatch(" "));
    }

    @Test
    public void fixedCatalogNeverRequiresAnInventedModelForMismatch() {
        assertEquals("活動蘑菇", MushroomDetector.classifyTemplateMatch(null));
        assertEquals("活動蘑菇", MushroomDetector.classifyTemplateMatch("\t"));
    }
}
