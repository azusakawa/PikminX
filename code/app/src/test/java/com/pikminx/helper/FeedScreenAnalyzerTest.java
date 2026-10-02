package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class FeedScreenAnalyzerTest {
    @Test
    public void formatsBasicAndFlowerNectarSearchQueries() {
        assertEquals("白色", FeedScreenAnalyzer.nectarSearchQuery("白色花瓣"));
        assertEquals("黃色", FeedScreenAnalyzer.nectarSearchQuery("黃色花瓣"));
        assertEquals("紅色", FeedScreenAnalyzer.nectarSearchQuery("紅色花瓣"));
        assertEquals("藍色", FeedScreenAnalyzer.nectarSearchQuery("藍色花瓣"));
        assertEquals("黃色 扶桑花", FeedScreenAnalyzer.nectarSearchQuery("黃色扶桑花"));
        assertEquals("紅色 蝴蝶蘭", FeedScreenAnalyzer.nectarSearchQuery("紅色蝴蝶蘭"));
        assertEquals("", FeedScreenAnalyzer.nectarSearchQuery("不存在的花"));
    }

    @Test
    public void countsOnlyConfirmedNectarDecrease() {
        assertEquals(Integer.valueOf(25), FeedScreenAnalyzer.consumedNectar(100, 75));
        assertEquals(Integer.valueOf(0), FeedScreenAnalyzer.consumedNectar(100, 100));
        assertNull(FeedScreenAnalyzer.consumedNectar(75, 100));
    }

    @Test
    public void readsCurrentCountOnlyFromBottomNectarArea() {
        List<PetalMatcher.Token> tokens = List.of(
                token("999", 100, 100),
                token("182", 520, 900));

        assertEquals(Integer.valueOf(182),
                FeedScreenAnalyzer.currentNectarCount(tokens, 1000, 1000));
        assertNull(FeedScreenAnalyzer.currentNectarCount(
                List.of(token("182", 100, 100)), 1000, 1000));
    }

    @Test
    public void stopsAtEffectivePetalLimit() {
        assertFalse(FeedScreenAnalyzer.hasReachedPetalLimit(249, 250));
        assertTrue(FeedScreenAnalyzer.hasReachedPetalLimit(250, 250));
        assertTrue(FeedScreenAnalyzer.hasReachedPetalLimit(1200, 1150));
    }

    @Test
    public void advancesPastEmptyNectarEvenWhenMinimumIsZero() {
        assertTrue(FeedScreenAnalyzer.shouldAdvanceNectar(0, 0));
        assertFalse(FeedScreenAnalyzer.shouldAdvanceNectar(1, 0));
        assertTrue(FeedScreenAnalyzer.shouldAdvanceNectar(39, 40));
        assertFalse(FeedScreenAnalyzer.shouldAdvanceNectar(40, 40));
    }

    @Test
    public void distinguishesOpenLightPanelFromGreenMap() {
        assertTrue(FeedScreenAnalyzer.isNectarPanelOpen(
                1000, 1000, (x, y) -> 0xfffafafa));
        assertFalse(FeedScreenAnalyzer.isNectarPanelOpen(
                1000, 1000, (x, y) -> 0xff4a9b50));
    }

    @Test
    public void targetAbsentFromConfirmedPanelTransitionsToSearch() {
        FeedScreenAnalyzer.NectarSearchAnalysis analysis =
                FeedScreenAnalyzer.analyzeVisibleNectarList(
                        List.of(),
                        "紅色花瓣",
                        1000,
                        1000,
                        (x, y) -> 0xfffafafa);

        assertEquals("visible-target-missing", analysis.reason());
        assertEquals(
                FeedScreenAnalyzer.NectarPanelAction.OPEN_SEARCH,
                FeedScreenAnalyzer.nextNectarPanelAction(
                        true, false, true, false, false, false));
    }

    @Test
    public void unconfirmedPanelNeverTransitionsToSearch() {
        assertEquals(
                FeedScreenAnalyzer.NectarPanelAction.WAIT,
                FeedScreenAnalyzer.nextNectarPanelAction(
                        false, false, true, true, false, false));
        assertEquals(
                FeedScreenAnalyzer.NectarPanelAction.WAIT,
                FeedScreenAnalyzer.nextNectarPanelAction(
                        true, false, false, true, false, false));
    }

    @Test
    public void invalidatesNectarListWhenPikminDetailAppears() {
        FeedScreenAnalyzer.NectarSearchAnalysis analysis =
                FeedScreenAnalyzer.analyzeVisibleNectarList(
                        List.of(
                                token("友好度", 300, 300),
                                token("已收集的花瓣 1 / 6", 300, 400)),
                        "黃色花瓣",
                        1000,
                        1000,
                        (x, y) -> 0xfffafafa);

        assertNull(analysis.selection());
        assertEquals("detail-open", analysis.reason());
    }

    @Test
    public void pairsNectarNameWithNearbyCount() {
        List<PetalMatcher.Token> tokens = List.of(
                token("白色精華", 300, 500),
                token("80", 320, 550),
                token("300", 300, 370),
                token("999", 800, 550));

        FeedScreenAnalyzer.NectarSelection selection = FeedScreenAnalyzer.findNectar(
                tokens, "白色花瓣", 1000, 1000);

        assertEquals("白色精華", selection.name());
        assertEquals(80, selection.count());
        assertEquals(300, selection.petalCount());
        assertEquals(415, selection.tapY());
    }

    @Test
    public void findsVisibleCardCandidatesWhenBasicRedLabelIsMissing() {
        int width = 638;
        int height = 1000;
        PetalMatcher.Token firstPetals = token("900", 120, 300);
        PetalMatcher.Token firstNectar = token("210", 120, 450);
        PetalMatcher.Token secondPetals = token("1,020", 319, 300);
        PetalMatcher.Token secondNectar = token("42", 319, 450);
        PetalMatcher.Token thirdPetals = token("1,149", 518, 300);
        PetalMatcher.Token thirdNectar = token("366", 518, 450);

        List<FeedScreenAnalyzer.NectarSelection> candidates =
                FeedScreenAnalyzer.findVisibleNectarCandidates(
                        List.of(
                                firstPetals, firstNectar,
                                secondPetals, secondNectar,
                                thirdPetals, thirdNectar),
                        "紅色花瓣", width, height);

        assertEquals(3, candidates.size());
        FeedScreenAnalyzer.NectarSelection red = candidates.stream()
                .filter(candidate -> candidate.count() == 366
                        && candidate.petalCount() == 1149)
                .findFirst()
                .orElse(null);
        assertNotNull(red);
        assertEquals("紅色精華", red.name());
        assertTrue(red.x() > width / 2);
        assertTrue(Math.abs(red.x() - thirdPetals.centerX()) <= width * 0.10f);
        assertTrue(red.tapY() > thirdPetals.centerY());
        assertTrue(red.tapY() < thirdNectar.centerY());
    }

    @Test
    public void findsInitialFourCardsAtObservedXiaomiVerticalSpacing() {
        int width = 1280;
        int height = 2772;
        List<PetalMatcher.Token> tokens = List.of(
                token("1,135", 125, 805), token("615 +", 125, 990),
                token("82", 425, 805), token("1,050 +", 425, 990),
                token("930", 725, 805), token("671 +", 725, 990),
                token("835", 1025, 805), token("727 +", 1025, 990));

        List<FeedScreenAnalyzer.NectarSelection> candidates =
                FeedScreenAnalyzer.findVisibleNectarCandidates(
                        tokens, "黃色花瓣", width, height);

        assertEquals(4, candidates.size());
        FeedScreenAnalyzer.NectarSelection yellow = candidates.get(1);
        assertEquals(1050, yellow.count());
        assertEquals(82, yellow.petalCount());
        assertTrue(yellow.tapY() > 805);
        assertTrue(yellow.tapY() < 1032);
    }

    @Test
    public void findsWhiteBaseNectarFromFirstCardGeometry() {
        List<PetalMatcher.Token> tokens = List.of(
                token("1,046", 160, 275),
                token("154 +", 200, 360),
                token("精華 白色美人蕉 黃色美人蕉 紅色美人蕉", 500, 400),
                token("101", 370, 360));

        FeedScreenAnalyzer.NectarSelection selection =
                FeedScreenAnalyzer.findSearchedNectar(
                        tokens, "白色花瓣", 1000, 1000);

        assertEquals("白色精華", selection.name());
        assertEquals(154, selection.count());
        assertEquals(1046, selection.petalCount());
        assertEquals(160, selection.x());
        assertEquals(320, selection.tapY());
    }

    @Test
    public void keepsZeroNectarAsAValidExhaustedCard() {
        List<PetalMatcher.Token> tokens = List.of(
                token("300", 160, 275),
                token("0 +", 200, 360),
                token("精華 白色美人蕉 黃色美人蕉", 500, 400));

        FeedScreenAnalyzer.NectarSelection selection =
                FeedScreenAnalyzer.findSearchedNectar(
                        tokens, "白色花瓣", 1000, 1000);

        assertNotNull(selection);
        assertEquals(0, selection.count());
        assertEquals(300, selection.petalCount());
    }

    @Test
    public void pairsPersistentPetalStockWithTheSameNectarCard() {
        List<PetalMatcher.Token> tokens = List.of(
                token("1,120", 160, 275),
                token("925", 410, 275),
                token("640 +", 160, 360),
                token("800 +", 410, 360),
                token("精華 黃色精華", 300, 400));

        FeedScreenAnalyzer.NectarSelection selection =
                FeedScreenAnalyzer.findSearchedNectar(
                        tokens, "白色花瓣", 1000, 1000);

        assertNotNull(selection);
        assertEquals(640, selection.count());
        assertEquals(1120, selection.petalCount());
    }

    @Test
    public void readsBaseNectarFromTwoFirstCardRoisAtDeviceScale() {
        List<PetalMatcher.Token> tokens = List.of(
                token("1,088", 150, 760),
                token("680 +", 180, 1008),
                token("195", 430, 760),
                token("2", 430, 1008));

        FeedScreenAnalyzer.NectarSearchAnalysis analysis =
                FeedScreenAnalyzer.analyzeSearchedNectar(
                        tokens, "黃色花瓣", 1260, 2800);

        assertEquals("matched", analysis.reason());
        assertTrue(analysis.nectarCountFound());
        assertTrue(analysis.petalCountFound());
        assertNotNull(analysis.selection());
        assertEquals(680, analysis.selection().count());
        assertEquals(1088, analysis.selection().petalCount());
        assertEquals(202, analysis.selection().x());
        assertEquals(896, analysis.selection().tapY());
    }

    @Test
    public void explainsWhichFirstCardCountIsMissing() {
        FeedScreenAnalyzer.NectarSearchAnalysis missingNectar =
                FeedScreenAnalyzer.analyzeSearchedNectar(
                        List.of(token("1,088", 150, 760)),
                        "黃色花瓣", 1260, 2800);
        FeedScreenAnalyzer.NectarSearchAnalysis missingPetals =
                FeedScreenAnalyzer.analyzeSearchedNectar(
                        List.of(token("680 +", 180, 1008)),
                        "黃色花瓣", 1260, 2800);

        assertEquals("base-nectar-count-missing", missingNectar.reason());
        assertFalse(missingNectar.nectarCountFound());
        assertTrue(missingNectar.petalCountFound());
        assertEquals("base-petal-count-missing", missingPetals.reason());
        assertTrue(missingPetals.nectarCountFound());
        assertFalse(missingPetals.petalCountFound());
    }

    @Test
    public void findsBlueBaseNectarFromFirstSearchResult() {
        List<PetalMatcher.Token> tokens = List.of(
                token("371", 160, 275),
                token("279 +", 200, 360),
                token("藍色精華 藍色扶桑花 藍色風鈴草 藍色山茶花", 500, 400),
                token("1", 370, 360));

        FeedScreenAnalyzer.NectarSelection selection =
                FeedScreenAnalyzer.findSearchedNectar(
                        tokens, "藍色花瓣", 1000, 1000);

        assertEquals("藍色精華", selection.name());
        assertEquals(279, selection.count());
        assertEquals(371, selection.petalCount());
        assertEquals(160, selection.x());
        assertEquals(320, selection.tapY());
    }

    @Test
    public void doesNotUseTopStockAsFirstCardCount() {
        List<PetalMatcher.Token> tokens = List.of(
                token("371", 160, 275),
                token("藍色精華 藍色扶桑花 藍色風鈴草 藍色山茶花", 500, 400));

        assertNull(FeedScreenAnalyzer.findSearchedNectar(
                tokens, "藍色花瓣", 1000, 1000));
    }

    @Test
    public void doesNotSelectCardWithoutPersistentPetalStock() {
        List<PetalMatcher.Token> tokens = List.of(
                token("279 +", 160, 360),
                token("藍色精華 藍色扶桑花", 300, 400));

        assertNull(FeedScreenAnalyzer.findSearchedNectar(
                tokens, "藍色花瓣", 1000, 1000));
    }

    @Test
    public void allowsExactNamedFlowerOutsideFirstSearchColumn() {
        List<PetalMatcher.Token> tokens = List.of(
                token("紅色百合", 600, 400),
                token("300", 600, 275),
                token("80", 620, 450));

        FeedScreenAnalyzer.NectarSelection selection =
                FeedScreenAnalyzer.findSearchedNectar(
                        tokens, "紅色百合", 1000, 1000);

        assertEquals("紅色百合", selection.name());
        assertEquals(80, selection.count());
        assertEquals(300, selection.petalCount());
    }

    @Test
    public void findsSingleSearchResultWhenOcrMisreadsFlowerName() {
        List<PetalMatcher.Token> tokens = List.of(
                new PetalMatcher.Token("Q黃色扶桑花", 93, 453, 409, 498),
                new PetalMatcher.Token("1,184", 161, 756, 250, 792),
                new PetalMatcher.Token("1,005", 222, 989, 317, 1027),
                new PetalMatcher.Token("黄色抉桑花", 87, 1084, 313, 1127));

        FeedScreenAnalyzer.NectarSelection selection =
                FeedScreenAnalyzer.findSearchedNectar(
                        tokens, "黃色扶桑花", 1280, 2772);

        assertNotNull(selection);
        assertEquals("黃色扶桑花", selection.name());
        assertEquals(1005, selection.count());
        assertEquals(1184, selection.petalCount());
        assertEquals(200, selection.x());
        assertEquals(876, selection.tapY());
    }

    @Test
    public void rejectsMultipleVisibleSearchResultsWhenNamesAreMisread() {
        List<PetalMatcher.Token> tokens = List.of(
                token("1,005", 200, 350),
                token("黄色抉桑花", 200, 400),
                token("900", 600, 350),
                token("任意辨識乙", 600, 400));

        assertNull(FeedScreenAnalyzer.findSearchedNectar(
                tokens, "黃色扶桑花", 1000, 1000));
    }

    @Test
    public void detectsNewBrightBloomPixelsButNotUnchangedFlower() {
        int width = 720;
        int height = 1280;
        FeedScreenAnalyzer.VisualSignature before = FeedScreenAnalyzer.capture(
                width, height, (x, y) -> 0xff303030);
        List<Long> glow = new ArrayList<>();
        for (int row = 28; row < 34; row++) {
            for (int column = 32; column < 38; column++) {
                glow.add((((long) row) << 32) | (column & 0xffffffffL));
            }
        }
        FeedScreenAnalyzer.PixelReader after = (x, y) -> {
            int column = Math.round((x / (float) width - 0.10f) / 0.80f * 71f);
            int row = Math.round((y / (float) height - 0.20f) / 0.58f * 71f);
            long key = (((long) row) << 32) | (column & 0xffffffffL);
            return glow.contains(key) ? 0xfffff2c0 : 0xff303030;
        };

        assertTrue(FeedScreenAnalyzer.hasNewBloomEffect(before, width, height, after));
        assertFalse(FeedScreenAnalyzer.hasNewBloomEffect(
                before, width, height, (x, y) -> 0xff303030));
    }

    @Test
    public void rejectsGlobalCameraMovementAsBloom() {
        int width = 720;
        int height = 1280;
        FeedScreenAnalyzer.VisualSignature before = FeedScreenAnalyzer.capture(
                width, height, (x, y) -> 0xff303030);

        assertFalse(FeedScreenAnalyzer.hasNewBloomEffect(
                before, width, height, (x, y) -> 0xfffff2c0));
    }

    @Test
    public void requiresTwoStableSearchFramesWithMatchingText() {
        assertFalse(FeedScreenAnalyzer.hasStableSearchEvidence(true, true, 1, 2));
        assertFalse(FeedScreenAnalyzer.hasStableSearchEvidence(true, false, 2, 2));
        assertFalse(FeedScreenAnalyzer.hasStableSearchEvidence(false, true, 2, 2));
        assertTrue(FeedScreenAnalyzer.hasStableSearchEvidence(true, true, 2, 2));
    }

    @Test
    public void confirmsOnlyTheSameNectarCardAcrossFrames() {
        FeedScreenAnalyzer.NectarSelection first =
                new FeedScreenAnalyzer.NectarSelection(
                        "紅色精華", 1104, 300, 160, 400, 315);
        FeedScreenAnalyzer.NectarSelection stable =
                new FeedScreenAnalyzer.NectarSelection(
                        "紅色精華", 1104, 300, 170, 410, 325);
        FeedScreenAnalyzer.NectarSelection differentCount =
                new FeedScreenAnalyzer.NectarSelection(
                        "紅色精華", 104, 300, 170, 410, 325);
        FeedScreenAnalyzer.NectarSelection differentPetalCount =
                new FeedScreenAnalyzer.NectarSelection(
                        "紅色精華", 1104, 301, 170, 410, 325);
        FeedScreenAnalyzer.NectarSelection differentCard =
                new FeedScreenAnalyzer.NectarSelection(
                        "紅色精華", 1104, 300, 500, 410, 325);

        assertTrue(FeedScreenAnalyzer.isSameNectarSelection(
                first, stable, 1000, 1000));
        assertFalse(FeedScreenAnalyzer.isSameNectarSelection(
                first, differentCount, 1000, 1000));
        assertFalse(FeedScreenAnalyzer.isSameNectarSelection(
                first, differentPetalCount, 1000, 1000));
        assertFalse(FeedScreenAnalyzer.isSameNectarSelection(
                first, differentCard, 1000, 1000));
        assertEquals("nectar-count-changed",
                FeedScreenAnalyzer.nectarSelectionStabilityReason(
                        first, differentCount, 1000, 1000));
        assertEquals("petal-count-changed",
                FeedScreenAnalyzer.nectarSelectionStabilityReason(
                        first, differentPetalCount, 1000, 1000));
    }

    @Test
    public void waitsThenRetriesAndStopsAtFourthGesture() {
        assertEquals(FeedScreenAnalyzer.NoEffectAction.WAIT,
                FeedScreenAnalyzer.noEffectAction(1, 4, 9_999L, 10_000L));
        assertEquals(FeedScreenAnalyzer.NoEffectAction.RETRY,
                FeedScreenAnalyzer.noEffectAction(3, 4, 10_000L, 10_000L));
        assertEquals(FeedScreenAnalyzer.NoEffectAction.GIVE_UP,
                FeedScreenAnalyzer.noEffectAction(4, 4, 10_000L, 10_000L));
        assertFalse(FeedScreenAnalyzer.hasStableBloom(1, 2));
        assertTrue(FeedScreenAnalyzer.hasStableBloom(2, 2));
    }

    @Test
    public void zoomOutPinchMovesBothFingersTowardMapCenter() {
        FeedScreenAnalyzer.ZoomOutPinch pinch =
                FeedScreenAnalyzer.zoomOutPinch(1000, 2000);

        assertEquals(200, pinch.leftStartX());
        assertEquals(430, pinch.leftEndX());
        assertEquals(800, pinch.rightStartX());
        assertEquals(570, pinch.rightEndX());
        assertEquals(1000, pinch.y());
        assertTrue(pinch.rightStartX() - pinch.leftStartX()
                > pinch.rightEndX() - pinch.leftEndX());
    }

    @Test
    public void recognizesPikminDetailFromFriendshipAndCollectedPetals() {
        assertTrue(FeedScreenAnalyzer.isPikminDetailOpen(List.of(
                token("友好度", 300, 300),
                token("已收集的花瓣 0 / 6", 300, 400))));
        assertFalse(FeedScreenAnalyzer.isPikminDetailOpen(List.of(
                token("友好度", 300, 300))));
    }

    @Test
    public void recognizesSharePreviewSeparatelyFromPikminDetail() {
        assertTrue(FeedScreenAnalyzer.isSharePreviewOpen(List.of(
                token("分享", 400, 900),
                token("儲存", 600, 900))));
        assertFalse(FeedScreenAnalyzer.isSharePreviewOpen(List.of(
                token("分享", 400, 900))));
    }

    @Test
    public void readsCombinedAndSplitPetalReceiptsOnlyInToastArea() {
        assertEquals(Integer.valueOf(8), FeedScreenAnalyzer.petalReceiptGain(
                List.of(token("紅色花瓣 +8", 500, 800)), 1000, 1000));
        assertEquals(Integer.valueOf(5), FeedScreenAnalyzer.petalReceiptGain(
                List.of(
                        token("紅色花瓣", 430, 800),
                        token("＋5", 570, 800)),
                1000,
                1000));
        assertNull(FeedScreenAnalyzer.petalReceiptGain(
                List.of(token("已收集的花瓣 1 / 6", 500, 800)), 1000, 1000));
        assertNull(FeedScreenAnalyzer.petalReceiptGain(
                List.of(token("紅色花瓣 +8", 500, 300)), 1000, 1000));
    }

    @Test
    public void countsChangedReceiptsWithinOneHarvestGestureWithoutRepeatingFrames() {
        assertEquals(40, FeedScreenAnalyzer.newPetalReceiptGain(40, null, false));
        assertEquals(0, FeedScreenAnalyzer.newPetalReceiptGain(40, 40, true));
        assertEquals(10, FeedScreenAnalyzer.newPetalReceiptGain(10, 40, true));
        assertEquals(0, FeedScreenAnalyzer.newPetalReceiptGain(null, 10, true));
        assertEquals(10, FeedScreenAnalyzer.newPetalReceiptGain(10, 10, false));
    }

    @Test
    public void releasesNectarAfterThreeStableReadsOrTheSafetyTimeout() {
        FeedScreenAnalyzer.NectarHoldProgress first = FeedScreenAnalyzer.observeNectarHold(
                100, 0, 99, 1_000L, 2_000L, 10_000L, 3);
        FeedScreenAnalyzer.NectarHoldProgress second = FeedScreenAnalyzer.observeNectarHold(
                first.count(), first.stableReads(), 99, 2_000L, 2_000L, 10_000L, 3);
        FeedScreenAnalyzer.NectarHoldProgress third = FeedScreenAnalyzer.observeNectarHold(
                second.count(), second.stableReads(), 99, 3_000L, 2_000L, 10_000L, 3);

        assertEquals(1, first.stableReads());
        assertEquals(2, second.stableReads());
        assertFalse(second.shouldRelease());
        assertEquals(3, third.stableReads());
        assertTrue(third.shouldRelease());
        assertTrue(FeedScreenAnalyzer.observeNectarHold(
                99, 2, null, 10_000L, 2_000L, 10_000L, 3).shouldRelease());
    }

    @Test
    public void buildsDenseFiveTurnWideEllipticalSpiralFromTheNectarHoldPoint() {
        List<FeedScreenAnalyzer.SpiralPoint> points =
                FeedScreenAnalyzer.ellipticalSpiral(1000, 2000, 160);

        assertEquals(161, points.size());
        assertEquals(new FeedScreenAnalyzer.SpiralPoint(500, 1080), points.get(0));
        assertEquals(new FeedScreenAnalyzer.SpiralPoint(860, 1080), points.get(160));
        assertTrue(points.stream().allMatch(point -> point.x() >= 140 && point.x() <= 860));
        assertTrue(points.stream().allMatch(point -> point.y() >= 580 && point.y() <= 1580));
        assertTrue(points.stream().noneMatch(point -> point.x() >= 820 && point.y() >= 1240));
        assertTrue(points.get(32).x() < points.get(64).x());
        assertTrue(points.get(64).x() < points.get(96).x());
        assertTrue(points.get(96).x() < points.get(128).x());
        assertTrue(points.get(128).x() < points.get(160).x());
        assertTrue(FeedScreenAnalyzer.ellipticalSpiral(0, 2000, 160).isEmpty());
    }

    @Test
    public void startsTheWideSpiralOnTheConfirmedBloomNearestTheCenter() {
        FeedScreenAnalyzer.BloomTarget farBright =
                new FeedScreenAnalyzer.BloomTarget(180, 600, 200);
        FeedScreenAnalyzer.BloomTarget nearest =
                new FeedScreenAnalyzer.BloomTarget(520, 1060, 80);

        FeedScreenAnalyzer.BloomTarget selected =
                FeedScreenAnalyzer.nearestBloomTargetToCenter(
                        List.of(farBright, nearest), 1000, 2000);
        List<FeedScreenAnalyzer.SpiralPoint> points =
                FeedScreenAnalyzer.ellipticalSpiralFromTarget(
                        1000, 2000, 160, selected);

        assertEquals(nearest, selected);
        assertEquals(161, points.size());
        assertEquals(new FeedScreenAnalyzer.SpiralPoint(520, 1060), points.get(0));
        assertEquals(new FeedScreenAnalyzer.SpiralPoint(860, 1080), points.get(160));
        assertTrue(FeedScreenAnalyzer.ellipticalSpiralFromTarget(
                1000, 2000, 160,
                new FeedScreenAnalyzer.BloomTarget(-1, 1060, 80)).isEmpty());
    }

    @Test
    public void keepsNectarAndObservedPetalStatisticsIndependent() {
        FeedScreenAnalyzer.FeedRoundStatistics statistics =
                FeedScreenAnalyzer.feedRoundStatistics(100, 75, 12);

        assertEquals(25, statistics.nectarConsumed());
        assertEquals(12, statistics.petalsObserved());
        assertNull(FeedScreenAnalyzer.feedRoundStatistics(75, 100, 12));
    }

    @Test
    public void requiresClosedPanelVisibleNectarAndNoDetailBeforeFeeding() {
        assertTrue(FeedScreenAnalyzer.isFeedViewReadyAfterNectarSelection(
                false, false, 1025));
        assertFalse(FeedScreenAnalyzer.isFeedViewReadyAfterNectarSelection(
                true, false, 1025));
        assertFalse(FeedScreenAnalyzer.isFeedViewReadyAfterNectarSelection(
                false, true, 1025));
        assertFalse(FeedScreenAnalyzer.isFeedViewReadyAfterNectarSelection(
                false, false, null));
    }

    @Test
    public void findsSeparatedBloomTargetsWithoutDraggingTheScene() {
        int width = 1000;
        int height = 2000;
        FeedScreenAnalyzer.VisualSignature before = FeedScreenAnalyzer.capture(
                width, height, (x, y) -> 0xff303030);
        FeedScreenAnalyzer.PixelReader after = glowClusters(
                width, height, List.of(new int[] {18, 24}, new int[] {52, 40}));

        List<FeedScreenAnalyzer.BloomTarget> targets =
                FeedScreenAnalyzer.findBloomTargets(before, width, height, after);

        assertEquals(2, targets.size());
        assertTrue(targets.stream().anyMatch(target -> target.x() < width / 2));
        assertTrue(targets.stream().anyMatch(target -> target.x() > width / 2));
    }

    @Test
    public void centralNectarEffectIsRejectedAndRealFlowerIsSelected() {
        int width = 1000;
        int height = 2000;
        FeedScreenAnalyzer.VisualSignature before = FeedScreenAnalyzer.capture(
                width, height, (x, y) -> 0xff303030);
        FeedScreenAnalyzer.PixelReader after = glowScene(
                width,
                height,
                new int[] {36, 42, 8},
                new int[] {52, 28, 1});

        List<FeedScreenAnalyzer.BloomTarget> targets =
                FeedScreenAnalyzer.findBloomTargets(before, width, height, after);
        FeedScreenAnalyzer.BloomTarget selected =
                FeedScreenAnalyzer.nearestHarvestBloomTarget(
                        targets, width, height, true);

        assertNotNull(selected);
        assertTrue(selected.x() > width * 0.60f);
    }

    @Test
    public void residualFeedEffectAloneIsNotABloomTarget() {
        int width = 1000;
        int height = 2000;
        FeedScreenAnalyzer.VisualSignature before = FeedScreenAnalyzer.capture(
                width, height, (x, y) -> 0xff303030);

        assertTrue(FeedScreenAnalyzer.findBloomTargets(
                before,
                width,
                height,
                glowScene(width, height, new int[] {36, 42, 8})).isEmpty());
    }

    @Test
    public void recentFeedEffectExclusionDoesNotPermanentlyBanCenteredFlowers() {
        FeedScreenAnalyzer.BloomTarget centered =
                new FeedScreenAnalyzer.BloomTarget(500, 1080, 120, 40, 40);

        assertNull(FeedScreenAnalyzer.nearestHarvestBloomTarget(
                List.of(centered), 1000, 2000, true));
        assertEquals(centered, FeedScreenAnalyzer.nearestHarvestBloomTarget(
                List.of(centered), 1000, 2000, false));
    }

    @Test
    public void skipsPreviouslyAttemptedBloomTargets() {
        FeedScreenAnalyzer.BloomTarget first =
                new FeedScreenAnalyzer.BloomTarget(400, 700, 100);
        FeedScreenAnalyzer.BloomTarget second =
                new FeedScreenAnalyzer.BloomTarget(700, 900, 90);

        assertEquals(second, FeedScreenAnalyzer.nextUnvisitedBloomTarget(
                List.of(first, second),
                List.of(new FeedScreenAnalyzer.BloomTarget(420, 720, 80)),
                1000,
                2000));
        assertNull(FeedScreenAnalyzer.nextUnvisitedBloomTarget(
                List.of(first), List.of(first), 1000, 2000));
    }

    @Test
    public void rejectsBloomTargetsWhenTheWholeSceneMoved() {
        int width = 1000;
        int height = 2000;
        FeedScreenAnalyzer.VisualSignature before = FeedScreenAnalyzer.capture(
                width, height, (x, y) -> 0xff303030);

        assertTrue(FeedScreenAnalyzer.findBloomTargets(
                before, width, height, (x, y) -> 0xfffff2c0).isEmpty());
    }

    @Test
    public void keepsBloomTargetsWhenSquadRearrangedLocally() {
        int width = 1000;
        int height = 2000;
        FeedScreenAnalyzer.VisualSignature before = FeedScreenAnalyzer.capture(
                width, height, (x, y) -> 0xff303030);
        FeedScreenAnalyzer.PixelReader after = (x, y) -> {
            int column = Math.round((x / (float) width - 0.10f) / 0.80f * 71f);
            int row = Math.round((y / (float) height - 0.20f) / 0.58f * 71f);
            if ((Math.abs(column - 26) <= 1 && Math.abs(row - 30) <= 1)
                    || (Math.abs(column - 46) <= 1 && Math.abs(row - 42) <= 1)) {
                return 0xfffff2c0;
            }
            return column >= 18 && column < 54 && row >= 18 && row < 54
                    ? 0xff606060
                    : 0xff303030;
        };

        List<FeedScreenAnalyzer.BloomTarget> targets =
                FeedScreenAnalyzer.findBloomTargets(before, width, height, after);

        assertEquals(2, targets.size());
    }

    @Test
    public void findsExistingYellowBloomClusterFromCurrentGardenFrame() {
        FeedScreenAnalyzer.BloomTarget target =
                FeedScreenAnalyzer.findExistingBloomTarget(
                        "黃色花瓣",
                        1000,
                        2000,
                        (x, y) -> {
                            if (Math.abs(x - 500) <= 70 && Math.abs(y - 700) <= 70) {
                                return 0xffffd52e;
                            }
                            if (Math.abs(x - 120) <= 20 && Math.abs(y - 500) <= 20) {
                                return 0xffffd52e;
                            }
                            return 0xff4a9b50;
                        });

        assertNotNull(target);
        assertTrue(Math.abs(target.x() - 500) <= 80);
        assertTrue(Math.abs(target.y() - 700) <= 80);
    }

    @Test
    public void rejectsLargeSolidColorRegionAsExistingBloom() {
        assertNull(FeedScreenAnalyzer.findExistingBloomTarget(
                "黃色花瓣",
                1000,
                2000,
                (x, y) -> Math.abs(x - 500) <= 120 && Math.abs(y - 700) <= 120
                        ? 0xffffd52e : 0xff4a9b50));
    }

    @Test
    public void matchesOnlyNearbyBloomTargetsAcrossFrames() {
        FeedScreenAnalyzer.BloomTarget first =
                new FeedScreenAnalyzer.BloomTarget(400, 700, 100, 40, 40);

        assertTrue(FeedScreenAnalyzer.isSameBloomTarget(
                first,
                new FeedScreenAnalyzer.BloomTarget(430, 730, 90, 46, 44),
                1000,
                2000));
        assertFalse(FeedScreenAnalyzer.isSameBloomTarget(
                first,
                new FeedScreenAnalyzer.BloomTarget(520, 900, 90, 40, 40),
                1000,
                2000));
        assertFalse(FeedScreenAnalyzer.isSameBloomTarget(
                first,
                new FeedScreenAnalyzer.BloomTarget(405, 705, 90, 100, 100),
                1000,
                2000));
    }

    private static FeedScreenAnalyzer.PixelReader glowClusters(
            int width, int height, List<int[]> centers) {
        return (x, y) -> {
            int column = Math.round((x / (float) width - 0.10f) / 0.80f * 71f);
            int row = Math.round((y / (float) height - 0.20f) / 0.58f * 71f);
            for (int[] center : centers) {
                if (Math.abs(column - center[0]) <= 1 && Math.abs(row - center[1]) <= 1) {
                    return 0xfffff2c0;
                }
            }
            return 0xff303030;
        };
    }

    private static FeedScreenAnalyzer.PixelReader glowScene(
            int width, int height, int[]... clusters) {
        return (x, y) -> {
            int column = Math.round((x / (float) width - 0.10f) / 0.80f * 71f);
            int row = Math.round((y / (float) height - 0.20f) / 0.58f * 71f);
            for (int[] cluster : clusters) {
                if (Math.abs(column - cluster[0]) <= cluster[2]
                        && Math.abs(row - cluster[1]) <= cluster[2]) {
                    return 0xfffff2c0;
                }
            }
            return 0xff303030;
        };
    }

    private static PetalMatcher.Token token(String text, int x, int y) {
        return new PetalMatcher.Token(text, x - 20, y - 10, x + 20, y + 10);
    }
}
