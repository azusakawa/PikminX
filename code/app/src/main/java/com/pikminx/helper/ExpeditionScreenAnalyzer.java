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
            return kind.name() + ":" + normalize(label) + ":" + xBucket + ":" + yBucket;
        }
    }

    record TitleEvidence(ItemKind kind, String label) {}

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
            IntBinaryOperator pixelAt) {
        return classifyWithBaseScreen(classify(tokens), tokens, width, height, pixelAt);
    }

    /**
     * A completed expedition can temporarily cover the expedition list with a modal.
     * The title is required so a normal list card's "領取" text is never treated as
     * this blocking dialog.
     */
    static boolean isReturnedExpeditionDialog(List<PetalMatcher.Token> tokens) {
        return containsAny(
                joined(tokens),
                "皮克敏從探險回來了",
                "皮克敏从探险回来了");
    }

    /** Adds visual evidence to a base classification without recalculating OCR rules. */
    static Screen classifyWithBaseScreen(
            Screen baseScreen,
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        Screen screen = baseScreen == null ? classify(tokens) : baseScreen;
        if (screen == Screen.UNKNOWN
                && hasDetailActionContext(tokens, width, height, pixelAt)) {
            return Screen.DETAIL;
        }
        if (screen == Screen.UNKNOWN
                && hasSelectionCounter(joined(tokens))
                && findPikminSearchButton(width, height, pixelAt) != null) {
            return Screen.PIKMIN_SELECTION;
        }
        return screen == Screen.UNKNOWN && hasScrolledExploreListEvidence(tokens, width, height)
                ? Screen.EXPLORE_LIST : screen;
    }

    /**
     * Some expedition detail variants replace the standard page heading with a decoration
     * promotion.  Keep the action text and detail metadata together so a list/status frame
     * containing only one of them is not promoted to a detail page.
     */
    private static boolean hasDetailActionContext(
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        if (findDetailAction(tokens, width, height, pixelAt) == null) {
            return false;
        }
        String text = joined(tokens);
        return containsAny(text,
                "距離", "距離：", "發現日", "发现日", "重量",
                "變更為稀有飾品", "变更为稀有饰品");
    }

    /** OCR or a unique current-frame outline; never fall back to a proportional tap point. */
    static Point findDetailActionForVerifiedScreen(
            Screen screen,
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        if (screen != Screen.DETAIL) return null;
        Point action = findDetailAction(tokens, width, height, pixelAt);
        return action != null ? action : findDetailButtonOutline(width, height, pixelAt);
    }

    /** The ROI limits perception; every returned coordinate comes from the detected outline. */
    private static Point findDetailButtonOutline(int width, int height, IntBinaryOperator pixelAt) {
        if (width < 1 || height < 1 || pixelAt == null) return null;
        int left = Math.round(width * 0.15f), right = Math.round(width * 0.85f);
        int top = Math.round(height * 0.20f), bottom = Math.round(height * 0.90f);
        int columns = right - left, rows = bottom - top;
        if (columns < 1 || rows < 1) return null;
        boolean[] teal = new boolean[columns * rows];
        boolean[] seen = new boolean[teal.length];
        int[] queue = new int[teal.length];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int color = pixelAt.applyAsInt(left + x, top + y);
                int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
                teal[y * columns + x] = green >= red + 40 && blue >= red + 30 && green >= blue;
            }
        }
        Point found = null;
        for (int seed = 0; seed < teal.length; seed++) {
            if (!teal[seed] || seen[seed]) continue;
            int head = 0, tail = 1;
            queue[0] = seed; seen[seed] = true;
            int minX = seed % columns, maxX = minX, minY = seed / columns, maxY = minY;
            while (head < tail) {
                int index = queue[head++], x = index % columns, y = index / columns;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= columns || ny < 0 || ny >= rows) continue;
                    int next = ny * columns + nx;
                    if (teal[next] && !seen[next]) {
                        seen[next] = true; queue[tail++] = next;
                    }
                }
            }
            int w = maxX - minX + 1, h = maxY - minY + 1;
            if (minX == 0 || maxX == columns - 1 || minY == 0 || maxY == rows - 1
                    || w < width * 0.20f || w > width * 0.70f
                    || h < height * 0.03f || h > height * 0.12f
                    || w < h * 2 || w > h * 6
                    || tail < w * h * 0.015f || tail > w * h * 0.25f) continue;
            int centerX = (minX + maxX) / 2, centerY = (minY + maxY) / 2;
            if (!tealNear(teal, columns, rows, centerX, minY)
                    || !tealNear(teal, columns, rows, centerX, maxY)
                    || !tealNear(teal, columns, rows, minX, centerY)
                    || !tealNear(teal, columns, rows, maxX, centerY)
                    || tealNear(teal, columns, rows, minX, minY)
                    || tealNear(teal, columns, rows, maxX, minY)
                    || tealNear(teal, columns, rows, minX, maxY)
                    || tealNear(teal, columns, rows, maxX, maxY)) continue;
            if (found != null) return null;
            found = new Point(left + centerX, top + centerY);
        }
        return found;
    }

    private static boolean tealNear(boolean[] mask, int width, int height, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && nx < width && ny >= 0 && ny < height && mask[ny * width + nx]) return true;
        }
        return false;
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

    static String detailActionDiagnostic(
            List<PetalMatcher.Token> tokens, int width, int height) {
        String directRejection = null;
        for (int index = 0; index < tokens.size(); index++) {
            PetalMatcher.Token token = tokens.get(index);
            String value = normalize(token.text());
            String matchKind = detailActionMatchKind(value);
            if (matchKind.equals("none")) {
                continue;
            }
            if (isDetailActionRegion(token, width, height)) {
                return "reason=direct_accepted token=" + index
                        + " match=" + matchKind
                        + " normalized=\"" + value + "\"";
            }
            if (directRejection == null) {
                directRejection = "reason=direct_outside_region token=" + index
                        + " match=" + matchKind
                        + " normalized=\"" + value + "\""
                        + " center=" + token.centerX() + "," + token.centerY();
            }
        }

        String pairRejection = null;
        for (int firstIndex = 0; firstIndex < tokens.size(); firstIndex++) {
            PetalMatcher.Token first = tokens.get(firstIndex);
            for (int secondIndex = 0; secondIndex < tokens.size(); secondIndex++) {
                if (firstIndex == secondIndex) {
                    continue;
                }
                PetalMatcher.Token second = tokens.get(secondIndex);
                String combined = normalize(first.text() + second.text());
                String matchKind = detailActionMatchKind(combined);
                if (matchKind.equals("none")) {
                    continue;
                }
                String reason = first.centerX() >= second.centerX() ? "pair_order"
                        : !isDetailActionRegion(first, width, height)
                                || !isDetailActionRegion(second, width, height)
                                        ? "pair_outside_region"
                        : Math.abs(first.centerY() - second.centerY()) > height * 0.04f
                                ? "pair_row_gap"
                        : second.left() - first.right() > width * 0.12f
                                ? "pair_horizontal_gap"
                        : "pair_accepted";
                String detail = "reason=" + reason
                        + " first=" + firstIndex
                        + " second=" + secondIndex
                        + " match=" + matchKind
                        + " combined=\"" + combined + "\""
                        + " firstCenter=" + first.centerX() + "," + first.centerY()
                        + " secondCenter=" + second.centerX() + "," + second.centerY()
                        + " rowDelta=" + Math.abs(first.centerY() - second.centerY())
                        + " horizontalGap=" + (second.left() - first.right());
                if (reason.equals("pair_accepted")) {
                    return detail;
                }
                if (pairRejection == null) {
                    pairRejection = detail;
                }
            }
        }
        if (directRejection != null) {
            return directRejection;
        }
        return pairRejection != null ? pairRejection : "reason=no_text_match";
    }

    static boolean matchesDetailActionText(String value) {
        return !detailActionMatchKind(value).equals("none");
    }

    static String detailActionMatchKind(String value) {
        if (containsAny(value, "前往探險", "前往探险", "前往探索", "前往探臉")) {
            return "exact";
        }
        if (!containsAny(value, "探險", "探险")) {
            return "none";
        }
        return containsThreeOfFourAlignedCharacters(value, "前往探險")
                        || containsThreeOfFourAlignedCharacters(value, "前往探险")
                ? "fuzzy_3_of_4" : "none";
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

    static boolean isDetailActionRegion(
            PetalMatcher.Token token, int width, int height) {
        return token.centerX() >= width * 0.20f
                && token.centerX() <= width * 0.80f
                && token.centerY() >= height * 0.20f
                && token.centerY() <= height * 0.90f;
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

    /** Disambiguates the Green Apple visual exception without weakening visual gates. */
    static TitleEvidence greenAppleTitleEvidence(List<PetalMatcher.Token> tokens) {
        String fruit = "";
        String pot = "";
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            if (containsAny(text, "蘋果", "苹果")) fruit = text;
            if (containsAny(text, "花苗")
                    && !text.equals("花苗")
                    && !containsAny(text, "花苗和水果")) {
                pot = text;
            }
        }
        if (!fruit.isEmpty() && pot.isEmpty()) return new TitleEvidence(ItemKind.FRUIT, fruit);
        if (!pot.isEmpty() && fruit.isEmpty()) return new TitleEvidence(ItemKind.POT, pot);
        return null;
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
        return findExploreNavigationAnchorForClassifier(tokens, width, height) != null;
    }

    private static boolean hasScrolledExploreListEvidence(
            List<PetalMatcher.Token> tokens, int width, int height) {
        Point anchor = findExploreNavigationAnchorForClassifier(tokens, width, height);
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

    /**
     * Accept the Xiaomi OCR substitution observed on the return-to-list frame.
     * The positional gate remains mandatory, and list classification additionally
     * requires card evidence in hasScrolledExploreListEvidence().
     */
    private static Point findExploreNavigationAnchorForClassifier(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            if (isExploreNavigationLabel(text)
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

    private static boolean isExploreNavigationLabel(String text) {
        // On the connected Xiaomi device, 探險 was recognized as 速險 while
        // the surrounding list/card OCR remained valid.
        return text.equals("探險") || text.equals("探险")
                || text.equals("速險") || text.equals("速险");
    }

    /**  同樣先定位探險頁籤，再從該安全錨點向上拉起面板。 */
    static Point findExploreTabAnchor(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
            if ((isExploreNavigationLabel(text) || containsAny(text, "探險", "探险"))
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

    /** Current selected-tab OCR may include an isolated border glyph, e.g. "|探險". */
    static boolean isExploreTabLabel(String text) {
        String label = normalize(text).replaceAll("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$", "");
        return label.equals("探險") || label.equals("探险");
    }

    /** 僅接受皮克敏選擇控制區內、完整匹配的「自動」文字中心。 */
    static Point findPikminAutoButton(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token token : tokens) {
            String value = normalize(token.text());
            if ((value.equals("自動") || value.equals("自动"))
                    && token.centerX() > width * 0.08f
                    && token.centerX() < width * 0.38f
                    && token.centerY() > height * 0.28f
                    && token.centerY() < height * 0.86f) {
                return new Point(token.centerX(), token.centerY());
            }
        }
        return null;
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

    /** 搜尋圖示沒有文字；只掃描左側控制區的深灰放大鏡，不依賴「自動」OCR。 */
    static Point findPikminSearchButton(
            int width, int height, IntBinaryOperator pixelAt) {
        if (width < 1 || height < 1 || pixelAt == null) {
            return null;
        }
        int left = Math.max(0, Math.round(width * 0.035f));
        int right = Math.min(width - 1, Math.round(width * 0.145f));
        int top = Math.max(0, Math.round(height * 0.30f));
        int bottom = Math.min(height - 1, Math.round(height * 0.48f));
        int radius = Math.max(6, Math.round(height * 0.012f));
        int[] rowCounts = new int[bottom - top + 1];
        long[] rowXSums = new long[rowCounts.length];
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (neutralDarkControl(pixelAt.applyAsInt(x, y))) {
                    rowCounts[y - top]++;
                    rowXSums[y - top] += x;
                }
            }
        }

        int window = radius * 2 + 1;
        int rolling = 0;
        int bestCount = 0;
        int bestStart = -1;
        for (int index = 0; index < rowCounts.length; index++) {
            rolling += rowCounts[index];
            if (index >= window) {
                rolling -= rowCounts[index - window];
            }
            if (index >= window - 1 && rolling > bestCount) {
                bestCount = rolling;
                bestStart = index - window + 1;
            }
        }
        if (bestStart < 0 || bestCount < Math.max(12, Math.round(width * 0.05f))) {
            return null;
        }
        long weightedX = 0, weightedY = 0;
        int count = 0;
        for (int index = bestStart; index < bestStart + window; index++) {
            weightedX += rowXSums[index];
            weightedY += (long) (top + index) * rowCounts[index];
            count += rowCounts[index];
        }
        return count == 0 ? null : new Point(
                Math.round((float) weightedX / count), Math.round((float) weightedY / count));
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

    /** Returns the current game's selection capacity, or -1 when the counter is absent. */
    static int pikminSelectionLimit(List<PetalMatcher.Token> tokens) {
        Matcher matcher = COUNTER.matcher(joined(tokens));
        while (matcher.find()) {
            int limit = Integer.parseInt(matcher.group(2));
            if (limit >= 1 && limit <= 12) return limit;
        }
        return -1;
    }

    /** Finds the fresh green circular X in the lower-left post-GO result state. */
    static Point findResultClose(Bitmap bitmap) {
        return findResultClose(bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
    }

    /** Pure pixel overload used by geometry regression tests. */
    static Point findResultClose(int width, int height, IntBinaryOperator pixelAt) {
        if (width < 32 || height < 64 || pixelAt == null) return null;
        int step = Math.max(2, width / 320), centerStep = step * 2;
        int right = Math.max(1, Math.round(width * 0.24f));
        int top = Math.max(0, height - Math.round(width * .28f));
        int bottom = Math.min(height - 1, height - Math.round(width * .02f));
        Point best = null; float bestScore = -1;
        for (float radiusFraction : new float[] {.045f, .055f, .065f}) {
            int radius = Math.max(centerStep, Math.round(width * radiusFraction));
            for (int centerY = top + radius; centerY + radius <= bottom; centerY += centerStep) {
                for (int centerX = radius; centerX + radius <= right; centerX += centerStep) {
                    int ring = 0, green = 0;
                    for (int y = centerY - radius; y <= centerY + radius; y += centerStep) {
                        for (int x = centerX - radius; x <= centerX + radius; x += centerStep) {
                            long distance = (long) (x - centerX) * (x - centerX)
                                    + (long) (y - centerY) * (y - centerY);
                            if (distance < radius * radius * .48f * .48f
                                    || distance > radius * radius * .88f * .88f) continue;
                            ring++;
                            if (resultCloseGreen(pixelAt.applyAsInt(x, y))) green++;
                        }
                    }
                    if (ring == 0 || green < ring * .80f) continue;
                    int inner = Math.round(radius * .55f), light = 0;
                    int minX = width, minY = height, maxX = -1, maxY = -1;
                    for (int y = centerY - inner; y <= centerY + inner; y += step) {
                        for (int x = centerX - inner; x <= centerX + inner; x += step) {
                            if ((long) (x - centerX) * (x - centerX)
                                    + (long) (y - centerY) * (y - centerY) > (long) inner * inner
                                    || !resultCloseLight(pixelAt.applyAsInt(x, y))) continue;
                            light++; minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        }
                    }
                    if (light < 12 || maxX - minX < radius * .35f || maxY - minY < radius * .35f
                            || maxX - minX > radius * .90f || maxY - minY > radius * .90f) continue;
                    int lightCenterX = (minX + maxX) / 2, lightCenterY = (minY + maxY) / 2;
                    int boxWidth = maxX - minX, boxHeight = maxY - minY;
                    if (Math.max(boxWidth, boxHeight) > Math.min(boxWidth, boxHeight) * 1.5f
                            || Math.abs(lightCenterX - centerX) > radius * .25f
                            || Math.abs(lightCenterY - centerY) > radius * .25f) continue;
                    int[] quadrants = new int[4];
                    for (int y = centerY - inner; y <= centerY + inner; y += step) {
                        for (int x = centerX - inner; x <= centerX + inner; x += step) {
                            if (!resultCloseLight(pixelAt.applyAsInt(x, y))) continue;
                            int dx = x - lightCenterX, dy = y - lightCenterY;
                            if (dx == 0 || dy == 0) continue;
                            quadrants[(dx > 0 ? 1 : 0) + (dy > 0 ? 2 : 0)]++;
                        }
                    }
                    if (java.util.Arrays.stream(quadrants).min().orElse(0) < 2) continue;
                    float score = green / (float) ring + light / 100f;
                    if (score > bestScore) {
                        bestScore = score; best = new Point(lightCenterX, lightCenterY);
                    }
                }
            }
        }
        return best;
    }

    private static boolean resultCloseGreen(int color) {
        int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
        return green >= 70 && green >= red + 8 && green >= blue - 25
                && Math.max(red, blue) - Math.min(red, blue) >= 8;
    }

    private static boolean resultCloseLight(int color) {
        int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
        int minimum = Math.min(red, Math.min(green, blue));
        int maximum = Math.max(red, Math.max(green, blue));
        return minimum >= 175 && maximum - minimum <= 80;
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

    private static boolean neutralDarkControl(int color) {
        int red = (color >>> 16) & 0xff;
        int green = (color >>> 8) & 0xff;
        int blue = color & 0xff;
        int minimum = Math.min(red, Math.min(green, blue));
        int maximum = Math.max(red, Math.max(green, blue));
        return maximum <= 175 && maximum - minimum <= 45;
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

    private ExpeditionScreenAnalyzer() {}
}
