package com.pikminx.helper;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntBinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** PikminX 自己的探險頁 OCR 判斷；不依賴  的座標或雲端辨識。 */
final class ExpeditionScreenAnalyzer {
    enum Screen {
        EXPLORE_LIST,
        DETAIL,
        PIKMIN_SELECTION,
        RESULT,
        UNKNOWN
    }

    enum ItemKind {
        FRUIT,
        POT
    }

    record Target(ItemKind kind, String label, int x, int y) {
        String confirmationKey(int width, int height) {
            int xBucket = Math.round(x * 10f / Math.max(1, width));
            int yBucket = Math.round(y * 10f / Math.max(1, height));
            return kind.name() + ":" + xBucket + ":" + yBucket;
        }
    }

    record Point(int x, int y) {}

    private static final Pattern COUNTER = Pattern.compile("(?<!\\d)(\\d{1,2})[/／|Il](\\d{1,2})(?!\\d)");
    private static final Pattern HAN_TEXT = Pattern.compile("\\p{IsHan}");
    private static final Pattern LATIN_TEXT = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT_TEXT = Pattern.compile("\\d");
    private static final Pattern CARD_DURATION = Pattern.compile(
            ".*\\d{1,2}(?:日|天|小時|小时|分鐘|分钟|分).*");

    static Screen classify(List<PetalMatcher.Token> tokens) {
        String text = joined(tokens);
        boolean selectionControl = hasExactToken(tokens, "自動", "自动", "GO", "篩選", "筛选", "排序");
        boolean selection = hasSelectionCounter(text) && selectionControl
                || hasExactToken(tokens, "自動", "自动")
                        && hasExactToken(tokens, "GO", "篩選", "筛选", "排序");
        if (selection) {
            return Screen.PIKMIN_SELECTION;
        }
        if (hasDetailPageMarker(text)) {
            return Screen.DETAIL;
        }
        if (containsAny(text, "派遣完成", "探險開始", "探险开始", "已出發", "已出发")) {
            return Screen.RESULT;
        }
        if (looksLikeExploreList(tokens)) {
            return Screen.EXPLORE_LIST;
        }
        int visibleItems = 0;
        for (PetalMatcher.Token token : tokens) {
            if (itemKind(normalize(token.text())) != null && ++visibleItems >= 2) {
                return Screen.EXPLORE_LIST;
            }
        }
        return Screen.UNKNOWN;
    }

    private static boolean hasDetailPageMarker(String text) {
        return containsAny(text,
                "派皮克敏出去探險吧", "派皮克敏出去探险吧", "派皮克敏出去探索吧",
                "派皮克敏出去探臉吧")
                || containsAny(text, "派皮克敏")
                        && containsAny(text, "出去探險吧", "出去探险吧", "出去探索吧", "出去探臉吧");
    }

    /** 詳細頁只接受固定頁面標記 OCR，不依賴按鈕顏色或背景亮度。 */
    static Screen classify(
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            IntBinaryOperator ignoredPixelAt) {
        Screen screen = classify(tokens);
        return screen == Screen.UNKNOWN && hasScrolledExploreListEvidence(tokens, width, height)
                ? Screen.EXPLORE_LIST : screen;
    }

    /** 只使用詳細頁中央偏下操作區的 OCR，並容許按鈕文字被拆成相鄰兩段。 */
    static Point findDetailAction(
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            IntBinaryOperator ignoredPixelAt) {
        for (PetalMatcher.Token token : tokens) {
            String value = normalize(token.text());
            if (matchesDetailActionText(value)
                    && isDetailActionRegion(token, width, height)) {
                return new Point(token.centerX(), token.centerY());
            }
        }
        for (PetalMatcher.Token first : tokens) {
            if (!isDetailActionRegion(first, width, height)) {
                continue;
            }
            for (PetalMatcher.Token second : tokens) {
                if (first == second || first.centerX() >= second.centerX()
                        || !isDetailActionRegion(second, width, height)
                        || Math.abs(first.centerY() - second.centerY()) > height * 0.04f
                        || second.left() - first.right() > width * 0.12f
                        || !matchesDetailActionText(normalize(first.text() + second.text()))) {
                    continue;
                }
                return new Point(
                        (Math.min(first.left(), second.left()) + Math.max(first.right(), second.right())) / 2,
                        (Math.min(first.top(), second.top()) + Math.max(first.bottom(), second.bottom())) / 2);
            }
        }
        return null;
    }

    private static boolean matchesDetailActionText(String value) {
        if (containsAny(value, "前往探險", "前往探险", "前往探索", "前往探臉")) {
            return true;
        }
        if (!containsAny(value, "探險", "探险")) {
            return false;
        }
        return containsThreeOfFourAlignedCharacters(value, "前往探險")
                || containsThreeOfFourAlignedCharacters(value, "前往探险");
    }

    private static boolean containsThreeOfFourAlignedCharacters(
            String value, String expected) {
        for (int start = 0; start + expected.length() <= value.length(); start++) {
            int matches = 0;
            for (int index = 0; index < expected.length(); index++) {
                if (value.charAt(start + index) == expected.charAt(index)) {
                    matches++;
                }
            }
            if (matches >= 3) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDetailActionRegion(
            PetalMatcher.Token token, int width, int height) {
        return token.centerX() >= width * 0.20f
                && token.centerX() <= width * 0.80f
                && token.centerY() >= height * 0.60f
                && token.centerY() <= height * 0.82f;
    }

    static boolean looksLikeExploreList(List<PetalMatcher.Token> tokens) {
        String text = joined(tokens);
        if (containsAny(text, "花苗和水果", "飾品一覽", "饰品一览")) {
            return true;
        }
        if (!containsAny(text, "探險", "探险")) {
            return false;
        }
        int durationCount = 0;
        for (PetalMatcher.Token token : tokens) {
            if (CARD_DURATION.matcher(normalize(token.text())).matches()
                    && ++durationCount >= 2) {
                return true;
            }
        }
        return false;
    }

    static Target findTarget(
            List<PetalMatcher.Token> tokens,
            ExpeditionTargetMode mode,
            int width,
            int height) {
        return findTarget(tokens, mode, width, height, null);
    }

    static Target findTarget(
            List<PetalMatcher.Token> tokens,
            ExpeditionTargetMode mode,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        List<Target> candidates = new ArrayList<>();
        int itemSectionTop = -1;
        for (PetalMatcher.Token token : tokens) {
            if (containsAny(normalize(token.text()), "花苗和水果")) {
                itemSectionTop = token.centerY();
                break;
            }
        }
        for (PetalMatcher.Token token : tokens) {
            if (token.centerY() < height * 0.24f || token.centerY() > height * 0.86f
                    || token.centerX() < width * 0.08f || token.centerX() > width * 0.92f
                    || itemSectionTop >= 0 && token.centerY() <= itemSectionTop) {
                continue;
            }
            String text = normalize(token.text());
            ItemKind kind = itemKind(text);
            if (kind == null && isLikelyFruitCardName(tokens, token, width, height)) {
                kind = ItemKind.FRUIT;
            }
            if (kind == null || belongsToActiveCard(tokens, token, width, height)) {
                continue;
            }
            if (pixelAt != null) {
                if (ReturnRewardDetector.looksLikeGift(
                        width, height, token.centerX(), token.top(), pixelAt)) {
                    continue;
                }
                boolean visualPot = looksLikePotStyle(
                        width, height, token.centerX(), token.top(), pixelAt);
                if ((mode == ExpeditionTargetMode.POT && !visualPot)
                        || (mode == ExpeditionTargetMode.FRUIT
                                && (visualPot || kind == ItemKind.POT))) {
                    continue;
                }
                kind = visualPot || kind == ItemKind.POT ? ItemKind.POT : ItemKind.FRUIT;
            }
            if (!mode.accepts(kind)) {
                continue;
            }
            int tapY = Math.max(token.top(), token.centerY() - Math.round(height * 0.025f));
            candidates.add(new Target(kind, token.text(), token.centerX(), tapY));
        }
        return candidates.stream()
                .max(Comparator.comparingInt(Target::y).thenComparingInt(Target::x))
                .orElse(null);
    }

    /** 九種花盆共通外觀：上半部有綠芽，中間有橫向棕色土壤。 */
    static boolean looksLikePotStyle(
            int width,
            int height,
            int centerX,
            int labelTop,
            IntBinaryOperator pixelAt) {
        int halfWidth = Math.round(width * 0.095f);
        int left = Math.max(0, centerX - halfWidth);
        int right = Math.min(width - 1, centerX + halfWidth);
        int top = Math.max(0, labelTop - Math.round(width * 0.19f));
        int bottom = Math.min(height - 1, labelTop - Math.round(width * 0.015f));
        if (right <= left || bottom <= top) {
            return false;
        }
        int step = Math.max(1, width / 540);
        int green = 0;
        int soil = 0;
        int sampled = 0;
        int soilMinX = right;
        int soilMaxX = left;
        int greenBottom = top + Math.round((bottom - top) * 0.65f);
        int soilTop = top + Math.round((bottom - top) * 0.35f);
        int soilBottom = top + Math.round((bottom - top) * 0.72f);
        for (int y = top; y <= bottom; y += step) {
            for (int x = left; x <= right; x += step) {
                int color = pixelAt.applyAsInt(x, y);
                sampled++;
                if (y <= greenBottom && isSproutGreen(color)) {
                    green++;
                }
                if (y >= soilTop && y <= soilBottom && isSoilBrown(color)) {
                    soil++;
                    soilMinX = Math.min(soilMinX, x);
                    soilMaxX = Math.max(soilMaxX, x);
                }
            }
        }
        return green * 1000 >= sampled * 5
                && soil * 1000 >= sampled * 8
                && soilMaxX - soilMinX >= (right - left) * 0.20f;
    }

    static boolean isExplorePanelExpanded(List<PetalMatcher.Token> tokens, int height) {
        Point exploreTab = findExploreTabAnchor(tokens, Integer.MAX_VALUE, height);
        if (exploreTab != null && exploreTab.y() <= height * 0.32f) {
            return true;
        }
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            if (token.centerY() <= height * 0.36f
                    && containsAny(text, "花苗和水果", "飾品一覽", "饰品一览",
                            "剩餘時間", "剩余时间", "發現日", "发现日")) {
                return true;
            }
        }
        return false;
    }

    static boolean isExplorePanelExpanded(
            List<PetalMatcher.Token> tokens, int width, int height) {
        // 可見卡片只能證明目前在探險清單，不能證明面板已上拉到掃描起點。
        return isExplorePanelExpanded(tokens, height);
    }

    static boolean hasExploreNavigationAnchor(
            List<PetalMatcher.Token> tokens, int width, int height) {
        return findExactExploreNavigationAnchor(tokens, width, height) != null;
    }

    private static boolean hasScrolledExploreListEvidence(
            List<PetalMatcher.Token> tokens, int width, int height) {
        Point anchor = findExactExploreNavigationAnchor(tokens, width, height);
        if (anchor == null) {
            return false;
        }
        for (PetalMatcher.Token token : tokens) {
            if (token.centerY() <= anchor.y() + height * 0.03f) {
                continue;
            }
            String text = normalize(token.text());
            if (CARD_DURATION.matcher(text).matches()
                    || containsSeedling(text)
                    || containsAny(text, "完成", "領取", "领取", "飾品一覽", "饰品一览")) {
                return true;
            }
        }
        return false;
    }

    private static Point findExactExploreNavigationAnchor(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            if ((text.equals("探險") || text.equals("探险"))
                    && token.centerY() > height * 0.08f
                    && token.centerY() < height * 0.65f
                    && (width == Integer.MAX_VALUE
                            || token.centerX() > width * 0.40f
                                    && token.centerX() < width * 0.82f)) {
                return new Point(token.centerX(), token.centerY());
            }
        }
        return null;
    }

    /**  同樣先定位探險頁籤，再從該安全錨點向上拉起面板。 */
    static Point findExploreTabAnchor(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            if (containsAny(text, "探險", "探险")
                    && token.centerY() > height * 0.08f
                    && token.centerY() < height * 0.65f
                    && (width == Integer.MAX_VALUE || token.centerX() > width * 0.40f)) {
                return new Point(token.centerX(), token.centerY());
            }
        }
        return null;
    }

    /** 圖二的清單頂端標記；到達後不再盲目滑動。 */
    static boolean isExploreListStart(List<PetalMatcher.Token> tokens) {
        String text = joined(tokens);
        return containsAny(text, "蘑菇", "磨菇")
                && containsAny(text, "今天還剩下", "今天还剩下", "今日還剩下", "今日还剩下");
    }

    static Point findTextAction(List<PetalMatcher.Token> tokens, String... labels) {
        for (PetalMatcher.Token token : tokens) {
            String value = normalize(token.text());
            for (String label : labels) {
                String expected = normalize(label);
                if (value.equals(expected) || value.contains(expected)) {
                    return new Point(token.centerX(), token.centerY());
                }
            }
        }
        return null;
    }

    /** 只接受皮克敏選取頁控制列上的「自動」，避免點到其他 OCR 文字。 */
    static Point findPikminAutoButton(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            if ((text.equals("自動") || text.equals("自动"))
                    && token.centerX() > width * 0.04f
                    && token.centerX() < width * 0.55f
                    && token.centerY() > height * 0.32f
                    && token.centerY() < height * 0.50f) {
                return new Point(token.centerX(), token.centerY());
            }
        }
        return null;
    }

    static String pikminAutoDiagnostic(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            if (!text.equals("自動") && !text.equals("自动")) {
                continue;
            }
            String reason = token.centerX() <= width * 0.04f ? "x_too_far_left"
                    : token.centerX() >= width * 0.55f ? "x_too_far_right"
                    : token.centerY() <= height * 0.32f ? "y_too_high"
                    : token.centerY() >= height * 0.50f ? "y_too_low"
                    : "accepted";
            return "text=\"" + token.text() + "\" bounds=["
                    + token.left() + "," + token.top() + ","
                    + token.right() + "," + token.bottom() + "] reason=" + reason;
        }
        return "reason=no_exact_auto_token";
    }

    /** GO 只會在選取完成後出現在右下角；限制區域可同時作為選取成功證據。 */
    static Point findPikminGoButton(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token token : tokens) {
            if (normalize(token.text()).equals("GO")
                    && token.centerX() > width * 0.60f
                    && token.centerX() < width * 0.98f
                    && token.centerY() > height * 0.70f
                    && token.centerY() < height * 0.98f) {
                return new Point(token.centerX(), token.centerY());
            }
        }
        return null;
    }

    /** 搜尋圖示沒有文字；以同列的「自動」OCR 錨點取得它的實際列位置。 */
    static Point findPikminSearchButton(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            if ((text.equals("自動") || text.equals("自动"))
                    && token.centerX() > width * 0.12f
                    && token.centerX() < width * 0.42f
                    && token.centerY() > height * 0.32f
                    && token.centerY() < height * 0.48f) {
                return new Point(Math.round(width * 0.088f), token.centerY());
            }
        }
        return null;
    }

    static boolean hasFullSelection(List<PetalMatcher.Token> tokens) {
        String text = joined(tokens);
        Matcher matcher = COUNTER.matcher(text);
        while (matcher.find()) {
            int selected = Integer.parseInt(matcher.group(1));
            int limit = Integer.parseInt(matcher.group(2));
            if (limit >= 1 && limit <= 12 && selected == limit) {
                return true;
            }
        }
        return false;
    }

    /** 回傳選取頁計數的已選數量；找不到合法的 0/10 類計數時回傳 -1。 */
    static int selectedPikminCount(List<PetalMatcher.Token> tokens) {
        Matcher matcher = COUNTER.matcher(joined(tokens));
        while (matcher.find()) {
            int selected = Integer.parseInt(matcher.group(1));
            int limit = Integer.parseInt(matcher.group(2));
            if (limit >= 1 && limit <= 12 && selected >= 0 && selected <= limit) {
                return selected;
            }
        }
        return -1;
    }

    /**
     * 尋找派遣結果頁左下角的亮色 X。門檻取自  的外觀思路，
     * 但座標、畫素與判斷全部由 PikminX 當前截圖重新計算。
     */
    static Point findResultClose(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = 0;
        int right = Math.max(1, Math.round(width * 0.18f));
        int top = Math.round(height * 0.82f);
        int bottom = Math.min(height - 1, Math.round(height * 0.955f));
        int step = Math.max(2, width / 320);
        int bestX = -1;
        int bestY = -1;
        float bestScore = 0f;
        for (int y = top; y <= bottom; y += step) {
            for (int x = left; x < right; x += step) {
                if (!brightNeutral(bitmap.getPixel(x, y))) {
                    continue;
                }
                int radius = Math.max(8, width / 45);
                int darkDiagonal = 0;
                int samples = 0;
                for (int delta = -radius; delta <= radius; delta += Math.max(2, step)) {
                    int x1 = x + delta;
                    int y1 = y + delta;
                    int x2 = x + delta;
                    int y2 = y - delta;
                    if (inside(x1, y1, width, height)) {
                        samples++;
                        if (darkTeal(bitmap.getPixel(x1, y1))) {
                            darkDiagonal++;
                        }
                    }
                    if (inside(x2, y2, width, height)) {
                        samples++;
                        if (darkTeal(bitmap.getPixel(x2, y2))) {
                            darkDiagonal++;
                        }
                    }
                }
                float diagonalRatio = samples == 0 ? 0f : (float) darkDiagonal / samples;
                float anchorPenalty = Math.abs(x - width * 0.085f) / width
                        + Math.abs(y - height * 0.91f) / height;
                float score = diagonalRatio - anchorPenalty;
                if (diagonalRatio >= 0.28f && score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return bestX < 0 ? null : resultCloseAnchor(width, height);
    }

    static Point resultCloseAnchor(int width, int height) {
        return new Point(Math.round(width * 0.098f), Math.round(height * 0.938f));
    }

    static String joined(List<PetalMatcher.Token> tokens) {
        StringBuilder builder = new StringBuilder();
        for (PetalMatcher.Token token : tokens) {
            builder.append(normalize(token.text()));
        }
        return builder.toString();
    }

    static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s：:！!]", "")
                .toUpperCase(Locale.ROOT);
    }

    private static ItemKind itemKind(String text) {
        return containsSeedling(text) ? ItemKind.POT : null;
    }

    /** 水果名稱不建表；只接受下方緊鄰地點文字的卡片名稱。 */
    private static boolean isLikelyFruitCardName(
            List<PetalMatcher.Token> tokens,
            PetalMatcher.Token candidate,
            int width,
            int height) {
        String text = normalize(candidate.text());
        if (!HAN_TEXT.matcher(text).find()
                || DIGIT_TEXT.matcher(text).find()
                || isTinySingleCharacterNoise(candidate, text, width, height)
                || containsSeedling(text)
                || containsGift(text)
                || containsAny(text, "蘑菇", "磨菇", "完成", "領取", "领取", "探險", "探险",
                        "飾品一覽", "饰品一览", "明信片", "花苗和水果")) {
            return false;
        }
        for (PetalMatcher.Token token : tokens) {
            if (token == candidate) {
                continue;
            }
            int gap = token.top() - candidate.bottom();
            boolean directlyBelow = gap >= -height * 0.01f && gap <= height * 0.09f;
            boolean sameColumn = Math.abs(token.centerX() - candidate.centerX()) <= width * 0.18f;
            String metadata = normalize(token.text());
            if (directlyBelow && sameColumn && metadata.length() >= 3
                    && (LATIN_TEXT.matcher(metadata).find()
                            || hasCardDurationBelow(tokens, token, width, height))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCardDurationBelow(
            List<PetalMatcher.Token> tokens,
            PetalMatcher.Token location,
            int width,
            int height) {
        for (PetalMatcher.Token token : tokens) {
            if (token == location) {
                continue;
            }
            int gap = token.top() - location.bottom();
            boolean directlyBelow = gap >= -height * 0.01f && gap <= height * 0.08f;
            boolean sameColumn = Math.abs(token.centerX() - location.centerX()) <= width * 0.18f;
            if (directlyBelow && sameColumn
                    && CARD_DURATION.matcher(normalize(token.text())).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean belongsToActiveCard(
            List<PetalMatcher.Token> tokens,
            PetalMatcher.Token candidate,
            int width,
            int height) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            boolean sameCardColumn = Math.abs(token.centerX() - candidate.centerX())
                    <= width * 0.18f;
            int statusGap = candidate.top() - token.bottom();
            boolean statusAbove = statusGap >= 0 && statusGap <= height * 0.10f;
            if (sameCardColumn && statusAbove
                    && (CARD_DURATION.matcher(text).matches()
                            || containsAny(text, "完成"))) {
                return true;
            }
            if (!containsAny(text, "剩餘時間", "剩余时间", "查看皮克敏", "中止", "終止", "使用無人機")) {
                continue;
            }
            boolean sameColumn = Math.abs(token.centerX() - candidate.centerX()) <= width * 0.24f;
            boolean sameRow = Math.abs(token.centerY() - candidate.centerY()) <= height * 0.10f;
            if (sameColumn && sameRow) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTinySingleCharacterNoise(
            PetalMatcher.Token token, String text, int width, int height) {
        return text.codePointCount(0, text.length()) == 1
                && token.right() - token.left() < width * 0.03f
                && token.bottom() - token.top() < height * 0.012f;
    }

    private static boolean hasSelectionCounter(String text) {
        Matcher matcher = COUNTER.matcher(text);
        while (matcher.find()) {
            int selected = Integer.parseInt(matcher.group(1));
            int limit = Integer.parseInt(matcher.group(2));
            if (limit >= 1 && limit <= 12 && selected >= 0 && selected <= limit) {
                return true;
            }
        }
        return text.matches(".*最多\\d{1,2}.{0,8}皮克敏.*");
    }

    private static boolean hasExactToken(
            List<PetalMatcher.Token> tokens, String... values) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            for (String value : values) {
                if (text.equals(normalize(value))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsSeedling(String text) {
        return text.endsWith(normalize("花苗"));
    }

    private static boolean containsGift(String text) {
        return containsAny(text, "禮品", "礼品");
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(normalize(value))) {
                return true;
            }
        }
        return false;
    }

    private static boolean brightNeutral(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        int minimum = Math.min(red, Math.min(green, blue));
        int maximum = Math.max(red, Math.max(green, blue));
        return minimum >= 145 && maximum >= 205 && maximum - minimum <= 95;
    }

    private static boolean darkTeal(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return red <= 145 && green >= 25 && green < 216 && blue >= 25 && blue < 216
                && green >= red + 3 && blue >= red - 5;
    }

    private static boolean isSproutGreen(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return green >= 55 && green >= red + 8 && green >= blue + 4;
    }

    private static boolean isSoilBrown(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return red >= 55 && red <= 200
                && green >= 35 && green <= 145
                && blue >= 10 && blue <= 120
                && red >= green + 12
                && green >= blue + 8;
    }

    private static boolean inside(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private ExpeditionScreenAnalyzer() {}
}
