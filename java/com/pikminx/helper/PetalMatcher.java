package com.pikminx.helper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/** 將 OCR token 配對到花瓣名稱，並以花盆右下角數字作為剩餘量。 */
final class PetalMatcher {
    record Token(String text, int left, int top, int right, int bottom) {
        int centerX() { return (left + right) / 2; }
        int centerY() { return (top + bottom) / 2; }
    }

    record Selection(String name, int count, int x, int y, int tapY) {}

    record PanelPull(int x, int startY, int endY) {}

    /** 由畫面中央上拉高度的 20%，讓花盆搜尋列進入固定位置。 */
    static PanelPull plantingPanelPull(int width, int height) {
        int startY = Math.round(height * 0.60f);
        return new PanelPull(
                Math.round(width * 0.50f),
                startY,
                startY - Math.round(height * 0.20f));
    }

    /** 從目前畫面找出序列中第一個仍高於門檻的可見花盆。 */
    static Selection findFlower(
            List<Token> tokens,
            List<String> allowed,
            String excluded,
            int minimumCount,
            int width,
            int height) {
        String excludedKey = normalize(excluded);
        int nextIndex = 0;
        if (!excludedKey.isEmpty()) {
            for (int index = 0; index < allowed.size(); index++) {
                if (normalize(allowed.get(index)).equals(excludedKey)) {
                    nextIndex = index + 1;
                    break;
                }
            }
        }
        if (nextIndex < 0 || nextIndex >= allowed.size()) {
            return null;
        }

        for (int index = nextIndex; index < allowed.size(); index++) {
            Selection next = findVisibleFlower(tokens, allowed.get(index), width, height);
            if (next == null) {
                return null;
            }
            if (next.count() > minimumCount) {
                return next;
            }
        }
        return null;
    }

    /** 以指定名稱尋找可見花盆，供相容性測試與流程使用。 */
    /**
     * Finds only the first user-configured flower for the initial planting selection.
     * Later rows must not replace the first row merely because they have more petals.
     */
    static Selection findInitialFlower(
            List<Token> tokens, List<String> sequence, int width, int height) {
        if (sequence.isEmpty()) {
            return null;
        }
        return findVisibleFlower(tokens, sequence.get(0), width, height);
    }

    static Selection findFlower(List<Token> tokens, String name, int width, int height) {
        return findVisibleFlower(tokens, name, width, height);
    }

    /** 搜尋框文字確認後，只接受完整目標名稱及其同欄右下數量；其他搜尋結果不影響判斷。 */
    static Selection findSearchedFlower(
            List<Token> tokens,
            String searchedFlower,
            int minimumCount,
            int width,
            int height) {
        String canonical = PetalCatalog.canonicalName(searchedFlower);
        if (canonical == null) {
            return null;
        }
        PetalPotDetector.Match match = PetalPotDetector.find(
                tokens,
                canonical,
                minimumCount,
                width,
                height,
                0.53f,
                0.96f,
                0.16f,
                0.20f,
                PetalMatcher::flowerNameKey);
        return match == null
                ? null
                : new Selection(
                        canonical,
                        match.count(),
                        match.x(),
                        match.labelY(),
                        Math.max(0, match.labelTop() - Math.round(height * 0.075f)));
    }

    /** Returns true when the selected pot has been changed away from the expected flower. */
    static boolean needsSelectionCorrection(String expectedFlower, Selection highlighted) {
        return highlighted != null
                && !normalize(expectedFlower).equals(normalize(highlighted.name()));
    }

    /** 取得目前花朵後面的下一個使用者目標。 */
    static String nextTarget(List<String> sequence, String current) {
        String currentKey = normalize(current);
        for (int i = 0; i < sequence.size(); i++) {
            if (normalize(sequence.get(i)).equals(currentKey)) {
                return i + 1 < sequence.size() ? sequence.get(i + 1) : null;
            }
        }
        return sequence.isEmpty() ? null : sequence.get(0);
    }

    /** 找出畫面底部的開始種花控制項。 */
    static Token findStartPlantingControl(List<Token> tokens, int width, int height) {
        for (Token token : tokens) {
            String key = normalize(token.text());
            if (token.centerX() >= 0
                    && token.centerX() <= width
                    && token.centerY() >= 0
                    && token.centerY() < height * 0.45
                    && (key.equals("開始種花") || key.equals("startplanting"))) {
                return token;
            }
        }
        return null;
    }

    /** 判斷目前是否已進入包含花盆卡片的遊戲選單。 */
    static boolean hasVisibleFlowerCard(
            List<Token> tokens, List<String> knownFlowers, int width, int height) {
        for (String flower : knownFlowers) {
            if (findVisibleFlower(tokens, flower, width, height) != null) {
                return true;
            }
        }
        return false;
    }

    /** 以 OCR 文字與粗略座標建立畫面指紋，供到底保護使用。 */
    static String screenSignature(List<Token> tokens) {
        // ponytail: 先用 OCR 指紋避免無限滑動；若遊戲改成固定文字但只變動像素，
        // 可再將截圖差異加入此保護，不影響花盆名稱判斷介面。
        List<Token> ordered = new ArrayList<>(tokens);
        ordered.sort(Comparator
                .comparingInt(Token::centerY)
                .thenComparingInt(Token::centerX));
        StringBuilder signature = new StringBuilder();
        for (Token token : ordered) {
            signature.append(normalize(token.text()))
                    .append('@')
                    .append(token.centerX() / 24)
                    .append(',')
                    .append(token.centerY() / 24)
                    .append(';');
        }
        return signature.toString();
    }

    /** 以顏色高亮分數找出玩家目前選中的花盆。 */
    static Selection findHighlightedFlower(
            List<Token> tokens,
            List<String> allowed,
            int width,
            int height,
            ToIntFunction<Selection> backgroundScore) {
        Selection best = null;
        int bestScore = -1;
        int secondScore = -1;
        for (String allowedName : allowed) {
            Selection candidate = findVisibleFlower(tokens, allowedName, width, height);
            if (candidate == null) {
                continue;
            }
            int score = backgroundScore.applyAsInt(candidate);
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = candidate;
            } else {
                secondScore = Math.max(secondScore, score);
            }
        }
        return best != null
                && bestScore >= 245
                && (secondScore < 0 || bestScore - secondScore >= 8)
                ? best
                : null;
    }

    /** 將名稱 token 與右下方數量 token 組成花盆選擇結果。 */
    private static Selection findVisibleFlower(
            List<Token> tokens, String allowedName, int width, int height) {
        PetalPotDetector.Match match = PetalPotDetector.find(
                tokens,
                allowedName,
                0,
                width,
                height,
                0.22f,
                0.99f,
                0.18f,
                0.10f,
                PetalMatcher::flowerNameKey);
        if (match == null) {
            return null;
        }
        String canonical = PetalCatalog.canonicalName(allowedName);
        String displayName = canonical == null ? allowedName : canonical;
        return new Selection(
                displayName,
                match.count(),
                match.x(),
                match.labelY(),
                Math.max(0, match.labelTop() - Math.round(height * 0.075f)));
    }

    /** 目錄內花朵套用與明信片相同的單一 token OCR 校正；舊測試名稱仍可正規化。 */
    private static String flowerNameKey(String value) {
        String canonical = PetalCatalog.canonicalName(value);
        return normalize(canonical == null ? value : canonical);
    }

    /** 找出與目標名稱相符且位於遊戲花盆區域的文字 token。 */
    private static Token matchingLabel(
            List<Token> tokens,
            Token firstLine,
            String allowedKey,
            int width,
            int height) {
        if (!isLabel(firstLine, width, height)) {
            return null;
        }
        if (normalize(firstLine.text()).equals(allowedKey)) {
            return firstLine;
        }
        for (Token secondLine : tokens) {
            if (secondLine == firstLine
                    || !isLabel(secondLine, width, height)
                    || secondLine.top() < firstLine.bottom()
                    || secondLine.top() - firstLine.bottom() > height * 0.04
                    || Math.abs(secondLine.centerX() - firstLine.centerX()) > width * 0.10
                    || !normalize(firstLine.text() + secondLine.text()).equals(allowedKey)) {
                continue;
            }
            return new Token(
                    firstLine.text() + secondLine.text(),
                    Math.min(firstLine.left(), secondLine.left()),
                    firstLine.top(),
                    Math.max(firstLine.right(), secondLine.right()),
                    secondLine.bottom());
        }
        return null;
    }

    /** 排除標題列與畫面邊緣，保留花盆名稱區域。 */
    private static boolean isLabel(Token token, int width, int height) {
        return parseCount(token.text()) == null
                && CardHighlight.contains(width, height, token.centerX(), token.centerY())
                // 花盆列會隨裝置比例與未滾動狀態上移；由精確名稱與右下數量配對負責消歧。
                && token.centerY() >= height * 0.22;
    }

    /** 在花盆右下角尋找最近的數量文字，避免誤用左上角精華數字。 */
    private static Token nearestCountAbove(List<Token> tokens, Token label, int width, int height) {
        Token best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Token token : tokens) {
            if (parseCount(token.text()) == null
                    || !CardHighlight.contains(
                            width, height, token.centerX(), token.centerY())) {
                continue;
            }
            int dx = Math.abs(token.centerX() - label.centerX());
            int dy = label.centerY() - token.centerY();
            if (dx > width * 0.18 || dy < 0 || dy > height * 0.10) {
                continue;
            }
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = token;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** 解析 OCR 數字，非純數字文字則視為無效。 */
    private static Integer parseCount(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s*\\+\\s*", "+")
                .trim();
        String digitGroup = "[0-9OoIl|]";
        String plain = digitGroup + "{1,5}";
        String grouped = digitGroup + "{1,3}(?:[,\\.·'\\s]" + digitGroup + "{3})+";
        if (!normalized.matches("\\+?(?:" + plain + "|" + grouped + ")\\+?")) {
            return null;
        }
        try {
            String digits = normalized
                    .replace('O', '0')
                    .replace('o', '0')
                    .replace('I', '1')
                    .replace('l', '1')
                    .replace('|', '1')
                    .replaceAll("[,\\.·'\\s+]", "");
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 統一 OCR 與使用者輸入的空白、標點與大小寫。 */
    static String normalize(String value) {
        return TextNormalizer.normalizeForMatch(value);
    }
}
