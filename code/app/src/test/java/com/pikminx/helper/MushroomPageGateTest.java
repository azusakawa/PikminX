package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class MushroomPageGateTest {
    @Test
    public void mushroomListWithRemainingCountIsEligible() {
        MushroomPageGate.Decision decision = MushroomPageGate.evaluate(List.of(
                token("探險", 600, 820),
                token("蘑菇", 70, 1010),
                token("今天還剩下 3 次", 70, 1070)));

        assertTrue(decision.eligible());
        assertEquals(MushroomPageGate.Reason.MUSHROOM_LIST, decision.reason());
    }

    @Test
    public void expeditionDetailPageIsEligible() {
        MushroomPageGate.Decision decision = MushroomPageGate.evaluate(List.of(
                token("派皮克敏出去探險吧", 340, 1480),
                token("前往探險", 420, 1580)));

        assertTrue(decision.eligible());
        assertEquals(MushroomPageGate.Reason.EXPEDITION_DETAIL, decision.reason());
    }

    @Test
    public void liveDetailPageWithMushroomTitleAndParticipationControlIsEligible() {
        MushroomPageGate.Decision decision = MushroomPageGate.evaluate(List.of(
                token("巨大華麗蘑菇", 370, 1580),
                token("華麗蘑菇僅在活動期間出現", 70, 1715),
                token("ゆ參加", 549, 1954)), 1280, 2772);

        assertTrue(decision.eligible());
        assertEquals(MushroomPageGate.Reason.EXPEDITION_DETAIL, decision.reason());
    }

    @Test
    public void mushroomTextWithoutLowerDetailParticipationControlIsRejected() {
        MushroomPageGate.Decision decision = MushroomPageGate.evaluate(List.of(
                token("地圖上的蘑菇", 60, 1320),
                token("參加", 60, 1500)), 1280, 2772);

        assertFalse(decision.eligible());
        assertEquals(MushroomPageGate.Reason.NO_PAGE_EVIDENCE, decision.reason());
    }

    @Test
    public void ordinaryGameMapCaptionDoesNotBecomeMushroomPageEvidence() {
        MushroomPageGate.Decision decision = MushroomPageGate.evaluate(List.of(
                token("推數5朵蘑菇", 75, 1460),
                token("步數", 560, 1560),
                token("商店", 760, 1920)));

        assertFalse(decision.eligible());
        assertEquals(MushroomPageGate.Reason.NO_PAGE_EVIDENCE, decision.reason());
    }

    @Test
    public void fullScreenWorldMapWithRelativeTerrainEvidenceIsEligible() {
        int width = 1280;
        int height = 2772;
        MushroomPageGate.Decision decision = MushroomPageGate.evaluate(
                List.of(token("步數", 560, 1560)),
                width,
                height,
                true,
                (x, y) -> 0xff4d9f68);

        assertTrue(decision.eligible());
        assertEquals(MushroomPageGate.Reason.MUSHROOM_MAP_ELIGIBLE, decision.reason());
    }

    @Test
    public void opaqueGamePanelWithTerrainOnlyAtBottomIsUnrelated() {
        int width = 1280;
        int height = 2772;
        MushroomPageGate.Decision decision = MushroomPageGate.evaluate(
                List.of(token("步數", 560, 1560)),
                width,
                height,
                true,
                (x, y) -> y > height * 0.62f ? 0xff4d9f68 : 0xfff8f8f8);

        assertFalse(decision.eligible());
        assertEquals(MushroomPageGate.Reason.UNRELATED_GAME_SCREEN, decision.reason());
    }

    @Test
    public void missingCurrentGameWindowIsNeverAuthorizedByMapPixels() {
        MushroomPageGate.Decision decision = MushroomPageGate.evaluate(
                List.of(), 1280, 2772, false, (x, y) -> 0xff4d9f68);

        assertFalse(decision.eligible());
        assertEquals(MushroomPageGate.Reason.GAME_NOT_FOREGROUND, decision.reason());
    }

    /** Regression boundary for the known false-positive Xiaomi game-map fixture. */
    @Test
    public void xiaomiRawGameMapNoMushroomTargetIsBlockedBeforeHitsAreShown() {
        MushroomPageGate.Decision decision = MushroomPageGate.evaluate(List.of(
                token("推數5朵蘑菇", 75, 1460),
                token("步數", 560, 1560),
                token("商店", 760, 1920)));

        assertFalse("detector hits must not be user-visible without page evidence",
                decision.eligible());
    }

    @Test
    public void missingOrUnrelatedPageEvidenceIsRejected() {
        assertFalse(MushroomPageGate.evaluate(List.of()).eligible());
        assertFalse(MushroomPageGate.evaluate(List.of(
                token("種花", 400, 500), token("開始", 500, 700))).eligible());
    }

    private static PetalMatcher.Token token(String text, int left, int top) {
        return new PetalMatcher.Token(text, left, top, left + 120, top + 48);
    }
}
