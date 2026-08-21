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

/** PikminX 自己的探險頁 OCR 判斷；不依賴 AutoCool 的座標或雲端辨識。 */
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
        String confirmationKey() {
            return kind.name() + ":" + normalize(label) + ":" + (x / 24) + ":" + (y / 24);
        }
    }

    record Point(int x, int y) {}

    private static final List<String> FRUIT_NAMES = List.of(
            "青蘋果", "紅蘋果", "蘋果", "苹果", "檸檬", "柠檬", "桃子", "梅子",
            "柳橙", "橘子", "葡萄", "草莓", "藍莓", "蓝莓", "鳳梨", "凤梨",
            "西瓜", "櫻桃", "樱桃", "梨");
    private static final Pattern COUNTER = Pattern.compile("(?<!\\d)(\\d{1,2})[/／|Il](\\d{1,2})(?!\\d)");

    static Screen classify(List<PetalMatcher.Token> tokens) {
        String text = joined(tokens);
        boolean selection = hasSelectionCounter(text)
                || (containsAny(text, "自動", "自动") && containsAny(text, "GO", "篩選", "筛选", "排序"));
        if (selection) {
            return Screen.PIKMIN_SELECTION;
        }
        if (containsAny(text, "前往探險", "前往探险", "前往探索", "派皮克敏")) {
            return Screen.DETAIL;
        }
        if (containsAny(text, "派遣完成", "探險開始", "探险开始", "已出發", "已出发")) {
            return Screen.RESULT;
        }
        if (looksLikeExploreList(text)) {
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

    /** OCR 可能仍留在清單文字；實際按鈕出現時就視為詳細頁。 */
    static Screen classify(
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            IntBinaryOperator pixelAt) {
        Screen screen = classify(tokens);
        if (screen == Screen.PIKMIN_SELECTION || screen == Screen.RESULT) {
            return screen;
        }
        return FlowerDetailActionDetector.find(width, height, pixelAt) == null
                ? screen : Screen.DETAIL;
    }

    static boolean looksLikeExploreList(String normalizedText) {
        String text = normalize(normalizedText);
        return containsAny(text, "花苗和水果", "飾品一覽", "饰品一览", "發現日", "发现日")
                || (text.contains("探險") || text.contains("探险"))
                && containsAny(text, "花苗", "小時", "小时", "分鐘", "分钟");
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
        for (PetalMatcher.Token token : tokens) {
            if (token.centerY() < height * 0.24f || token.centerY() > height * 0.86f
                    || token.centerX() < width * 0.08f || token.centerX() > width * 0.92f) {
                continue;
            }
            String text = normalize(token.text());
            ItemKind kind = itemKind(text);
            if (kind == null || belongsToActiveCard(tokens, token, width, height)) {
                continue;
            }
            if (pixelAt != null) {
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

    /** 將局部放大 OCR 的座標還原後，沿用同一套派遣目標過濾。 */
    static Target findFocusedTarget(
            List<PetalMatcher.Token> tokens,
            ExpeditionTargetMode mode,
            int width,
            int height,
            int cropTop,
            int scale) {
        List<PetalMatcher.Token> mapped = new ArrayList<>(tokens.size());
        for (PetalMatcher.Token token : tokens) {
            mapped.add(new PetalMatcher.Token(
                    token.text(),
                    token.left() / scale,
                    cropTop + token.top() / scale,
                    token.right() / scale,
                    cropTop + token.bottom() / scale));
        }
        return findTarget(mapped, mode, width, height);
    }

    static Target findFocusedTarget(
            List<PetalMatcher.Token> tokens,
            ExpeditionTargetMode mode,
            int width,
            int height,
            int cropTop,
            int scale,
            int focusedWidth,
            int focusedHeight,
            IntBinaryOperator focusedPixelAt) {
        List<PetalMatcher.Token> mapped = new ArrayList<>(tokens.size());
        for (PetalMatcher.Token token : tokens) {
            mapped.add(new PetalMatcher.Token(
                    token.text(),
                    token.left() / scale,
                    cropTop + token.top() / scale,
                    token.right() / scale,
                    cropTop + token.bottom() / scale));
        }
        IntBinaryOperator mappedPixelAt = (x, y) -> {
            int focusedX = x * scale;
            int focusedY = (y - cropTop) * scale;
            if (focusedX < 0 || focusedX >= focusedWidth
                    || focusedY < 0 || focusedY >= focusedHeight) {
                return Color.WHITE;
            }
            return focusedPixelAt.applyAsInt(focusedX, focusedY);
        };
        return findTarget(mapped, mode, width, height, mappedPixelAt);
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

    /** AutoCool 同樣先定位探險頁籤，再從該安全錨點向上拉起面板。 */
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

    /**
     * 尋找派遣結果頁左下角的亮色 X。門檻取自 AutoCool 的外觀思路，
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
        if (containsSeedling(text)) {
            return ItemKind.POT;
        }
        for (String fruit : FRUIT_NAMES) {
            if (text.contains(normalize(fruit))) {
                return ItemKind.FRUIT;
            }
        }
        return text.contains("檬") && text.length() <= 12 ? ItemKind.FRUIT : null;
    }

    private static boolean belongsToActiveCard(
            List<PetalMatcher.Token> tokens,
            PetalMatcher.Token candidate,
            int width,
            int height) {
        for (PetalMatcher.Token token : tokens) {
            String text = normalize(token.text());
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

    private static boolean containsSeedling(String text) {
        return containsAny(text, "花苗", "大花苗", "苗");
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
