package com.pikminx.helper;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 共用的花盆 OCR 判斷核心。
 *
 * <p>一般監控可接受完整且相符的名稱 token；自動換花與明信片流程在搜尋文字已確認時，
 * 可改用唯一可見結果。所有流程都從同一欄名稱上方尋找最近數量。</p>
 */
final class PetalPotDetector {
    record Match(int count, int x, int labelY, int labelTop) {}

    private PetalPotDetector() {}

    /** 找出畫面中最靠上、再最靠左的精確目標花盆。 */
    static Match find(
            List<PetalMatcher.Token> tokens,
            String targetName,
            int minimumCount,
            int width,
            int height,
            float minimumLabelY,
            float maximumLabelY,
            float maximumCountXDistance,
            float maximumCountYDistance,
            Function<String, String> nameKey) {
        String targetKey = nameKey.apply(targetName);
        if (targetKey == null || targetKey.isBlank()) {
            return null;
        }
        return tokens.stream()
                .filter(token -> isExactLabel(
                        token, targetKey, width, height,
                        minimumLabelY, maximumLabelY, nameKey))
                .map(label -> matchCount(
                        tokens,
                        label,
                        minimumCount,
                        width,
                        height,
                        maximumCountXDistance,
                        maximumCountYDistance))
                .filter(match -> match != null)
                .min(Comparator.comparingInt(Match::labelY).thenComparingInt(Match::x))
                .orElse(null);
    }

    /**
     * 搜尋框已由呼叫端確認時，只依完整單列的幾何位置與最近數量找唯一結果。
     * 花名內容不參與比對，避免不同裝置的 OCR 字形誤識令正確結果失效。
     */
    static Match findSingleVisible(
            List<PetalMatcher.Token> tokens,
            int minimumCount,
            int width,
            int height,
            float minimumLabelY,
            float maximumLabelY,
            float maximumCountXDistance,
            float maximumCountYDistance,
            Predicate<String> labelCandidate) {
        List<Match> matches = tokens.stream()
                .filter(token -> isVisibleLabel(
                        token, width, height, minimumLabelY, maximumLabelY, labelCandidate))
                .map(label -> matchCount(
                        tokens,
                        label,
                        minimumCount,
                        width,
                        height,
                        maximumCountXDistance,
                        maximumCountYDistance))
                .filter(match -> match != null)
                .collect(Collectors.toList());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** 名稱必須完整存在於同一個 OCR token，禁止合併相鄰或上下文字。 */
    private static boolean isExactLabel(
            PetalMatcher.Token token,
            String targetKey,
            int width,
            int height,
            float minimumLabelY,
            float maximumLabelY,
            Function<String, String> nameKey) {
        String tokenKey = nameKey.apply(token.text());
        return tokenKey != null
                && tokenKey.equals(targetKey)
                && token.centerX() > width * 0.03f
                && token.centerX() < width * 0.97f
                && token.centerY() > height * minimumLabelY
                && token.centerY() < height * maximumLabelY;
    }

    private static boolean isVisibleLabel(
            PetalMatcher.Token token,
            int width,
            int height,
            float minimumLabelY,
            float maximumLabelY,
            Predicate<String> labelCandidate) {
        return labelCandidate.test(token.text())
                && token.centerX() > width * 0.03f
                && token.centerX() < width * 0.97f
                && token.centerY() > height * minimumLabelY
                && token.centerY() < height * maximumLabelY;
    }

    /** 數量沿用明信片流程的同欄、名稱上方最近值規則。 */
    private static Match matchCount(
            List<PetalMatcher.Token> tokens,
            PetalMatcher.Token label,
            int minimumCount,
            int width,
            int height,
            float maximumCountXDistance,
            float maximumCountYDistance) {
        PetalMatcher.Token best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PetalMatcher.Token token : tokens) {
            Integer count = parseCount(token.text());
            if (count == null || count < minimumCount) {
                continue;
            }
            int dx = Math.abs(token.centerX() - label.centerX());
            int dy = label.top() - token.centerY();
            if (dx > width * maximumCountXDistance
                    || dy < 0
                    || dy > height * maximumCountYDistance) {
                continue;
            }
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = token;
                bestDistance = distance;
            }
        }
        Integer count = best == null ? null : parseCount(best.text());
        return count == null
                ? null
                : new Match(count, label.centerX(), label.centerY(), label.top());
    }

    /** 解析花盆數量並容忍 OCR 常見的 O、I、l 與千分位誤識。 */
    private static Integer parseCount(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s*\\+\\s*", "+")
                .trim();
        if (!normalized.matches("\\+?[0-9OoIl|][0-9OoIl|,.'\\s]{0,7}\\+?")) {
            return null;
        }
        try {
            return Integer.parseInt(normalized
                    .replace('O', '0')
                    .replace('o', '0')
                    .replace('I', '1')
                    .replace('l', '1')
                    .replace('|', '1')
                    .replaceAll("[,.'\\s+]", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
