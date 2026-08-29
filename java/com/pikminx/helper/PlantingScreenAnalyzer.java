package com.pikminx.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/** 只供自動種花使用的頁面與主要控制項判斷。 */
final class PlantingScreenAnalyzer {
    enum Screen {
        MAP_WITH_ENTRY,
        MAP_VISIBLE_NO_ENTRY,
        PLANTING_MENU,
        AMBIGUOUS,
        UNKNOWN
    }

    record Point(int x, int y) {}

    enum EntrySource {
        NONE,
        WHISTLE_RELATIVE
    }

    record EntryEvidence(
            EntrySource source,
            Point whistleAnchor,
            CardHighlight.Bounds whistleSearchBounds,
            int whistleScore) {}

    record Detection(
            Screen screen,
            Point mapEntry,
            Point startControl,
            Point stopControl,
            EntryEvidence entryEvidence) {}

    private static final List<String> MAP_ANCHORS = List.of(
            "步數", "商店", "好友", "通知", "飾品", "boost", "shop");
    private static final List<String> ASSISTANT_OVERLAY_ANCHORS = List.of(
            "自動撈花",
            "自動種花",
            "辨識中",
            "準備搜尋花盆",
            "點懸浮圖示",
            "正在確認地圖種花入口",
            "已進入種花畫面");

    private PlantingScreenAnalyzer() {}

    static Detection analyze(
            List<PetalMatcher.Token> tokens,
            List<String> knownFlowers,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        List<PetalMatcher.Token> gameTokens = withoutAssistantOverlay(tokens);
        boolean mapAnchored = containsAny(gameTokens, MAP_ANCHORS);
        CardHighlight.MapEntryMatch whistleMatch =
                CardHighlight.findMapPlantingEntryAboveWhistle(width, height, pixelAt);
        Point mapEntry = point(whistleMatch.point());
        EntryEvidence entryEvidence = new EntryEvidence(
                mapEntry == null ? EntrySource.NONE : EntrySource.WHISTLE_RELATIVE,
                point(whistleMatch.anchor()),
                whistleMatch.searchBounds(),
                whistleMatch.score());

        CardHighlight.PlantingMenuControls menuControls =
                CardHighlight.findPlantingMenuControls(width, height, pixelAt);
        CardHighlight.Point start = menuControls == null ? null : menuControls.startControl();
        CardHighlight.Point stop = menuControls == null ? null : menuControls.stopControl();
        if (menuControls != null) {
            return new Detection(
                    Screen.PLANTING_MENU,
                    null,
                    point(start),
                    point(stop),
                    entryEvidence);
        }
        return new Detection(
                mapEntry != null
                        ? Screen.MAP_WITH_ENTRY
                        : mapAnchored
                                ? Screen.MAP_VISIBLE_NO_ENTRY
                                : Screen.UNKNOWN,
                mapEntry,
                point(start),
                point(stop),
                entryEvidence);
    }

    /**
     * @deprecated 僅保留相容與回歸測試；頁面判斷不接受無障礙節點旗標捷徑。
     */
    @Deprecated
    static Detection analyze(
            List<PetalMatcher.Token> tokens,
            List<String> knownFlowers,
            int width,
            int height,
            IntBinaryOperator pixelAt,
            boolean ignoredSemanticStartVisible,
            boolean ignoredSemanticStopVisible) {
        return analyze(tokens, knownFlowers, width, height, pixelAt);
    }

    private static List<PetalMatcher.Token> withoutAssistantOverlay(
            List<PetalMatcher.Token> tokens) {
        List<PetalMatcher.Token> filtered = new ArrayList<>(tokens.size());
        for (PetalMatcher.Token token : tokens) {
            if (!containsAny(token.text(), ASSISTANT_OVERLAY_ANCHORS)) {
                filtered.add(token);
            }
        }
        return filtered;
    }

    private static boolean containsAny(
            List<PetalMatcher.Token> tokens, List<String> anchors) {
        for (PetalMatcher.Token token : tokens) {
            if (containsAny(token.text(), anchors)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, List<String> anchors) {
        String text = PetalMatcher.normalize(value);
        for (String anchor : anchors) {
            if (text.contains(PetalMatcher.normalize(anchor))) {
                return true;
            }
        }
        return false;
    }

    private static Point point(CardHighlight.Point point) {
        return point == null ? null : new Point(point.x(), point.y());
    }
}
