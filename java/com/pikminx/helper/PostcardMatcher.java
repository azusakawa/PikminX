package com.pikminx.helper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 將明信片流程的 OCR 文字轉成頁面狀態與可安全點擊的座標。 */
final class PostcardMatcher {
    enum Page {
        MAP,
        FLOWER_DETAIL,
        WARNING,
        PETAL_SELECTION,
        PIKMIN_SELECTION,
        POSTCARD_RECEIVED,
        UNKNOWN
    }

    record Target(String text, int x, int y) {}
    record PetalPot(String name, int count, int x, int y) {}

    private static final Pattern SELECTED_COUNT = Pattern.compile("(\\d)\\s*/\\s*5");

    static Page detectPage(List<PetalMatcher.Token> tokens, int width, int height) {
        if (hasText(tokens, "持有的明信片") && hasText(tokens, "接收")) {
            return Page.POSTCARD_RECEIVED;
        }
        if (hasText(tokens, "選擇皮克敏出去取回明信片")
                || (hasText(tokens, "取回明信片") && findGo(tokens) != null)
                || (hasSelectedPikminCounter(tokens) && findSortControl(tokens, height) != null)
                || isSortMenuVisible(tokens, height)) {
            return Page.PIKMIN_SELECTION;
        }
        if (hasText(tokens, "選擇要使用的花瓣") || hasText(tokens, "下一步")) {
            return Page.PETAL_SELECTION;
        }
        if (hasText(tokens, "接受並繼續") && hasText(tokens, "注意")) {
            return Page.WARNING;
        }
        if (findUsePetals(tokens) != null) {
            return Page.FLOWER_DETAIL;
        }
        // 完整花種與地點雙行氣泡可保守判定為地圖；其他語言或 OCR 不完整時，
        // 服務層仍會再用 MapPostcardBubbleDetector 的像素結果補判。
        if (hasConfirmedMapFlowerBubble(tokens, width, height)) {
            return Page.MAP;
        }
        return Page.UNKNOWN;
    }

    static Target findUsePetals(List<PetalMatcher.Token> tokens) {
        return findText(tokens, "使用花瓣就能獲得明信片");
    }

    static Target findAcceptContinue(List<PetalMatcher.Token> tokens) {
        return findText(tokens, "接受並繼續");
    }

    static Target findNext(List<PetalMatcher.Token> tokens) {
        return findExactText(tokens, "下一步");
    }

    static Target findGo(List<PetalMatcher.Token> tokens) {
        return exactTokens(tokens, "go", "g0", "60").stream()
                .findFirst()
                .map(PostcardMatcher::target)
                .orElse(null);
    }

    static Target findReceive(List<PetalMatcher.Token> tokens) {
        return findExactText(tokens, "接收");
    }

    static Target findDiscard(List<PetalMatcher.Token> tokens) {
        Target traditional = findExactText(tokens, "捨棄");
        return traditional != null ? traditional : findExactText(tokens, "舍棄");
    }

    static Target findDiscard(List<PetalMatcher.Token> tokens, int width, int height) {
        Target discard = findDiscard(tokens);
        if (discard != null) {
            return discard;
        }
        Target receive = findReceive(tokens);
        if (detectPage(tokens, width, height) != Page.POSTCARD_RECEIVED
                || receive == null
                || receive.x() <= width * 0.50f
                || receive.x() >= width * 0.92f
                || receive.y() <= height * 0.55f
                || receive.y() >= height * 0.90f) {
            return null;
        }
        return new Target("捨棄", width - receive.x(), receive.y());
    }

    static Target findMapFlowerName(
            List<PetalMatcher.Token> tokens, int width, int height) {
        List<PetalMatcher.Token> candidates = new ArrayList<>();
        for (PetalMatcher.Token token : tokens) {
            String key = normalize(token.text());
            if (key.contains("花朵")
                    && !key.contains("花瓣")
                    && token.centerX() > width * 0.10
                    && token.centerX() < width * 0.90
                    && token.centerY() > height * 0.12
                    && token.centerY() < height * 0.82) {
                candidates.add(token);
            }
        }
        PetalMatcher.Token token = candidates.stream()
                .min(Comparator.comparingInt(candidate ->
                        Math.abs(candidate.centerX() - width / 2)
                                + Math.abs(candidate.centerY() - height / 2)))
                .orElse(null);
        if (token == null) {
            return null;
        }
        int flowerEnd = token.text().indexOf("花朵") + "花朵".length();
        String flowerName = flowerEnd >= "花朵".length()
                ? token.text().substring(0, flowerEnd).trim()
                : token.text().trim();
        return new Target(flowerName, token.centerX(), token.centerY());
    }

    static Target findMapPostcardName(
            List<PetalMatcher.Token> tokens, int width, int height) {
        Target splitBubble = findSplitMapPostcardName(tokens, width, height);
        if (splitBubble != null) {
            return splitBubble;
        }
        for (PetalMatcher.Token merged : tokens) {
            Target mergedLocation = findMergedMapPostcardName(merged, width, height);
            if (mergedLocation != null) {
                return mergedLocation;
            }
        }

        List<PetalMatcher.Token> candidates = new ArrayList<>();
        for (PetalMatcher.Token token : tokens) {
            String key = normalize(token.text());
            if (key.contains("花朵")
                    && !key.contains("花瓣")
                    && token.centerX() > width * 0.10
                    && token.centerX() < width * 0.90
                    && token.centerY() > height * 0.12
                    && token.centerY() < height * 0.82) {
                candidates.add(token);
            }
        }
        PetalMatcher.Token token = candidates.stream()
                .min(Comparator.comparingInt(candidate ->
                        Math.abs(candidate.centerX() - width / 2)
                                + Math.abs(candidate.centerY() - height / 2)))
                .orElse(null);
        if (token == null) {
            return null;
        }
        String text = token.text().trim();
        String[] parts = text.split("\\s+");
        if (parts.length > 1) {
            String trailing = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length))
                    .trim();
            if (isDetailPostcardNameText(trailing)) {
                return new Target(trailing, token.centerX(), token.centerY());
            }
        }
        int flowerEnd = text.indexOf("花朵");
        if (flowerEnd >= 0) {
            String trailing = text.substring(flowerEnd + "花朵".length()).trim();
            if (isDetailPostcardNameText(trailing)) {
                return new Target(trailing, token.centerX(), token.centerY());
            }
        }
        PetalMatcher.Token nextLine = tokens.stream()
                .filter(candidate -> candidate != token)
                .filter(candidate -> isDetailPostcardNameText(candidate.text()))
                .filter(candidate -> candidate.top() >= token.bottom())
                .filter(candidate -> candidate.top() - token.bottom() <= height * 0.05)
                .filter(candidate -> Math.abs(candidate.centerX() - token.centerX()) <= width * 0.18)
                .min(Comparator.comparingInt(PetalMatcher.Token::top))
                .orElse(null);
        return nextLine == null
                ? null
                : new Target(nextLine.text().trim(), nextLine.centerX(), nextLine.centerY());
    }

    /** A real map flower bubble contains both its species line and its location line. */
    static boolean hasConfirmedMapFlowerBubble(
            List<PetalMatcher.Token> tokens, int width, int height) {
        if (findSplitMapPostcardName(tokens, width, height) != null) {
            return true;
        }
        return tokens.stream().anyMatch(token -> {
            Target location = findMergedMapPostcardName(token, width, height);
            return location != null;
        });
    }

    private static Target findMergedMapPostcardName(
            PetalMatcher.Token token, int width, int height) {
        if (!isMapSpeciesCandidate(token, width, height)) {
            return null;
        }
        String text = token.text().trim();
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        for (int start = 1; start < parts.length; start++) {
            String location = String.join(
                    " ", java.util.Arrays.copyOfRange(parts, start, parts.length)).trim();
            if (isDetailPostcardNameText(location)) {
                return new Target(location, token.centerX(), token.centerY());
            }
        }
        return null;
    }

    /**
     * 實機地圖氣泡通常由兩個 OCR token 組成：上行是花種，下行是明信片地點。
     * 以顏色開頭的花種作為錨點，避免把附近的一般地圖文字誤認成明信片。
     */
    private static Target findSplitMapPostcardName(
            List<PetalMatcher.Token> tokens, int width, int height) {
        List<Target> candidates = new ArrayList<>();
        for (PetalMatcher.Token species : tokens) {
            if (!isMapSpeciesCandidate(species, width, height)) {
                continue;
            }
            for (PetalMatcher.Token location : tokens) {
                if (location == species
                        || !isDetailPostcardNameText(location.text())
                        || location.top() < species.bottom() - height * 0.01
                        || location.top() - species.bottom() > height * 0.06
                        || Math.abs(location.centerX() - species.centerX()) > width * 0.20) {
                    continue;
                }
                candidates.add(new Target(
                        location.text().trim(),
                        (species.centerX() + location.centerX()) / 2,
                        (species.centerY() + location.centerY()) / 2));
            }
        }
        return candidates.stream()
                .max(Comparator
                        .comparingInt((Target candidate) ->
                                mapLocationTextQuality(candidate.text()))
                        .thenComparingInt(candidate -> -mapCenterDistance(
                                candidate, width, height)))
                .orElse(null);
    }

    /**
     * Multiple OCR models can describe the same bubble with slightly different boxes.
     * Prefer one coherent writing system over a nearby hybrid of unrelated glyphs.
     */
    private static int mapLocationTextQuality(String value) {
        int letters = 0;
        int symbols = 0;
        boolean han = false;
        boolean kana = false;
        boolean latin = false;
        boolean hangul = false;
        boolean devanagari = false;
        boolean otherLetter = false;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)) {
                letters++;
                Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                han |= script == Character.UnicodeScript.HAN;
                kana |= script == Character.UnicodeScript.HIRAGANA
                        || script == Character.UnicodeScript.KATAKANA;
                latin |= script == Character.UnicodeScript.LATIN;
                hangul |= script == Character.UnicodeScript.HANGUL;
                devanagari |= script == Character.UnicodeScript.DEVANAGARI;
                otherLetter |= !han && !kana && !latin && !hangul && !devanagari;
            } else if (!Character.isDigit(codePoint) && !Character.isWhitespace(codePoint)) {
                symbols++;
            }
        }
        int score = letters * 4 + Math.min(value.length(), 18) - symbols * 4;
        boolean japanese = han || kana;
        if (japanese) {
            score += 100;
        } else if (hangul || devanagari || otherLetter) {
            score += 90;
        } else if (latin) {
            score += 75;
        }
        // English suffixes and numbers are legal, but a mixed-script line is less
        // trustworthy than an otherwise equivalent native-script map label.
        if (latin && (japanese || hangul || devanagari || otherLetter)) {
            score -= 45;
        }
        return score;
    }

    private static int mapCenterDistance(Target candidate, int width, int height) {
        return Math.abs(candidate.x() - width / 2)
                + Math.abs(candidate.y() - height / 2);
    }

    private static boolean isMapSpeciesCandidate(
            PetalMatcher.Token token, int width, int height) {
        String key = normalize(token.text());
        boolean colorPrefix = key.startsWith("白色")
                || key.startsWith("黃色")
                || key.startsWith("紅色")
                || key.startsWith("藍色")
                || key.startsWith("紫色")
                || key.startsWith("灰色");
        return colorPrefix
                && token.text().matches(".*\\p{IsHan}.*")
                && token.centerX() > width * 0.10
                && token.centerX() < width * 0.90
                && token.centerY() > height * 0.12
                && token.centerY() < height * 0.82;
    }

    static Target findDetailPostcardName(
            List<PetalMatcher.Token> tokens, int width, int height) {
        int minY = Math.round(height * 0.05f);
        int maxY = Math.round(height * 0.26f);
        List<PetalMatcher.Token> candidates = new ArrayList<>();
        for (PetalMatcher.Token token : tokens) {
            if (isDetailPostcardNameCandidate(token, width, minY, maxY)) {
                candidates.add(token);
            }
        }
        for (PetalMatcher.Token first : candidates) {
            for (PetalMatcher.Token second : candidates) {
                if (second == first
                        || second.top() < first.bottom()
                        || second.top() - first.bottom() > height * 0.04
                        || Math.abs(second.centerX() - first.centerX()) > width * 0.12) {
                    continue;
                }
                String merged = first.text() + second.text();
                if (!isDetailPostcardNameText(merged)) {
                    continue;
                }
                return new Target(
                        merged,
                        (Math.min(first.left(), second.left())
                                + Math.max(first.right(), second.right())) / 2,
                        (first.top() + second.bottom()) / 2);
            }
        }
        int anchorY = (minY + maxY) / 2;
        PetalMatcher.Token token = candidates.stream()
                .min(Comparator.comparingInt(candidate ->
                        Math.abs(candidate.centerX() - width / 2)
                                + Math.abs(candidate.centerY() - anchorY)))
                .orElse(null);
        return token == null ? null : new Target(token.text(), token.centerX(), token.centerY());
    }

    static Target findDetailFlowerName(
            List<PetalMatcher.Token> tokens, int width, int height) {
        Target usePetals = findUsePetals(tokens);
        int minY = Math.round(height * 0.42f);
        int maxY = usePetals == null
                ? Math.round(height * 0.78f)
                : Math.min(usePetals.y() - Math.round(height * 0.03f),
                        Math.round(height * 0.78f));
        if (maxY <= minY) {
            maxY = Math.round(height * 0.78f);
        }
        List<PetalMatcher.Token> candidates = new ArrayList<>();
        for (PetalMatcher.Token token : tokens) {
            if (isDetailTitleCandidate(token, width, minY, maxY)) {
                candidates.add(token);
            }
        }
        for (PetalMatcher.Token first : candidates) {
            for (PetalMatcher.Token second : candidates) {
                if (first == second
                        || second.top() < first.bottom()
                        || second.top() - first.bottom() > height * 0.04
                        || Math.abs(second.centerX() - first.centerX()) > width * 0.12) {
                    continue;
                }
                String merged = first.text() + second.text();
                if (!isDetailTitleText(merged)) {
                    continue;
                }
                return new Target(
                        merged,
                        (Math.min(first.left(), second.left())
                                + Math.max(first.right(), second.right())) / 2,
                        (first.top() + second.bottom()) / 2);
            }
        }
        int anchorY = (minY + maxY) / 2;
        PetalMatcher.Token token = candidates.stream()
                .min(Comparator.comparingInt(candidate ->
                        Math.abs(candidate.centerX() - width / 2)
                                + Math.abs(candidate.centerY() - anchorY)))
                .orElse(null);
        return token == null ? null : new Target(token.text(), token.centerX(), token.centerY());
    }

    static boolean isSortMenuVisible(List<PetalMatcher.Token> tokens, int height) {
        return findFavoriteMenuItem(tokens, height) != null
                && (hasText(tokens, "排序")
                        || hasText(tokens, "友好度")
                        || hasText(tokens, "飾品"));
    }

    static Target findSortControl(List<PetalMatcher.Token> tokens, int height) {
        return tokens.stream()
                .filter(token -> normalize(token.text()).startsWith("喜愛")
                        || normalize(token.text()).startsWith("自動")
                        || normalize(token.text()).startsWith("發現日")
                        || normalize(token.text()).startsWith("種類")
                        || normalize(token.text()).startsWith("友好度")
                        || normalize(token.text()).startsWith("飾品"))
                .filter(token -> token.centerY() < height * 0.68)
                .min(Comparator.comparingInt(PetalMatcher.Token::centerY))
                .map(PostcardMatcher::target)
                .orElse(null);
    }

    static Target findFavoriteMenuItem(List<PetalMatcher.Token> tokens, int height) {
        return tokens.stream()
                .filter(token -> normalize(token.text()).startsWith("喜愛"))
                .filter(token -> token.centerY() > height * 0.50)
                .max(Comparator.comparingInt(PetalMatcher.Token::centerY))
                .map(PostcardMatcher::target)
                .orElse(null);
    }

    static int selectedPikminCount(List<PetalMatcher.Token> tokens) {
        for (PetalMatcher.Token token : tokens) {
            String text = Normalizer.normalize(token.text(), Normalizer.Form.NFKC);
            Matcher matcher = SELECTED_COUNT.matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return 0;
    }

    /**
     * 標題可能被 OCR 切成多個 token；用 (0/5) 計數與排序控制作為穩定備援特徵。
     */
    private static boolean hasSelectedPikminCounter(List<PetalMatcher.Token> tokens) {
        for (PetalMatcher.Token token : tokens) {
            String text = Normalizer.normalize(token.text(), Normalizer.Form.NFKC);
            if (SELECTED_COUNT.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /** 選擇清單中最上、最左的皮克敏卡片；座標由 OCR 花名標籤推算。 */
    static Target findFirstPikmin(List<PetalMatcher.Token> tokens, int width, int height) {
        List<Target> candidates = findPikminCandidates(tokens, width, height);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** 依畫面的列、欄順序回傳可點選的皮克敏卡片。 */
    static List<Target> findPikminCandidates(
            List<PetalMatcher.Token> tokens, int width, int height) {
        return tokens.stream()
                .filter(token -> token.centerY() > height * 0.46)
                .filter(token -> token.centerY() < height * 0.88)
                .filter(token -> token.centerX() < width * 0.92)
                .filter(token -> token.text().matches(".*\\p{IsHan}.*"))
                .filter(token -> !isPikminUiText(token.text()))
                .sorted(Comparator
                        .comparingInt((PetalMatcher.Token token) -> token.centerY() / 40)
                        .thenComparingInt(PetalMatcher.Token::centerX))
                .map(token -> new Target(
                        token.text(),
                        token.centerX(),
                        Math.max(0, token.top() - Math.round(height * 0.065f))))
                .collect(Collectors.toList());
    }

    /**
     * First visible Pikmin row, in strict left-to-right order.
     *
     * <p>The game always renders five equal columns in this picker. OCR labels
     * can be missing or split, so they must not decide which card is the first,
     * second, ... fifth. Relative slots also remain valid across device sizes.</p>
     */
    static List<Target> findTopRowPikminSlots(int width, int height) {
        return findPikminSelectionSlots(width, height).subList(0, 5);
    }

    /** 選皮頁紅框內的前 12 格；以畫面比例維持跨解析度的 5 欄順序。 */
    static List<Target> findPikminSelectionSlots(int width, int height) {
        float[] columnFractions = {0.13f, 0.32f, 0.51f, 0.70f, 0.89f};
        float[] rowFractions = {0.515f, 0.660f, 0.805f};
        List<Target> result = new ArrayList<>(12);
        for (float row : rowFractions) {
            for (float column : columnFractions) {
                result.add(new Target(
                        "pikmin-slot-" + (result.size() + 1),
                        Math.round(width * column),
                        Math.round(height * row)));
                if (result.size() == 12) {
                    return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    /** 找出目前可見且至少有 requiredCount 片的花瓣花盆。 */
    static PetalPot findAvailablePetalPot(
            List<PetalMatcher.Token> tokens,
            String targetPotName,
            int requiredCount,
            int width,
            int height) {
        String canonical = PostcardPotCatalog.canonicalName(targetPotName);
        if (canonical == null) {
            return null;
        }
        PetalPotDetector.Match match = PetalPotDetector.find(
                tokens,
                canonical,
                requiredCount,
                width,
                height,
                0.53f,
                0.96f,
                0.16f,
                0.20f,
                value -> {
                    String name = PostcardPotCatalog.canonicalName(value);
                    return name == null ? "" : normalize(name);
                });
        return match == null
                ? null
                : new PetalPot(
                        canonical,
                        match.count(),
                        match.x(),
                        Math.max(0, match.labelTop() - Math.round(height * 0.075f)));
    }

    /**
     * 搜尋文字已由無障礙節點確認後，選取畫面中唯一有足夠數量的花盆。
     * OCR 花名只作為卡片位置錨點，不必與搜尋文字相符。
     */
    static PetalPot findSingleVisiblePetalPot(
            List<PetalMatcher.Token> tokens,
            String searchedPotName,
            int requiredCount,
            int width,
            int height) {
        String canonical = PostcardPotCatalog.canonicalName(searchedPotName);
        if (canonical == null) {
            return null;
        }
        PetalPotDetector.Match match = PetalPotDetector.findSingleVisible(
                tokens,
                requiredCount,
                width,
                height,
                0.53f,
                0.96f,
                0.16f,
                0.20f,
                PostcardMatcher::looksLikeResultLabel);
        return match == null
                ? null
                : new PetalPot(
                        canonical,
                        match.count(),
                        match.x(),
                        Math.max(0, match.labelTop() - Math.round(height * 0.075f)));
    }

    private static boolean looksLikeResultLabel(String value) {
        if (value == null || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return false;
        }
        return value.codePoints().filter(Character::isLetter).count() >= 2;
    }

    /**
     * 建立僅含可滾動花盆名稱的穩定指紋。
     *
     * <p>狀態列時間、遊戲背景與懸浮提示會持續改變；若把它們納入到底判斷，
     * 清單即使已停在底部仍會被誤認為新畫面。這裡沿用花盆名稱的相對區域，
     * 並保留粗略座標以辨識清單確實有移動。</p>
     */
    static String petalListSignature(
            List<PetalMatcher.Token> tokens, int width, int height) {
        List<PetalMatcher.Token> listLabels = tokens.stream()
                .filter(token -> isPetalLabel(token, width, height))
                .collect(Collectors.toList());
        return PetalMatcher.screenSignature(listLabels);
    }

    /** 只從花盆清單區域收集完整單列 OCR 名稱。 */
    static List<String> visiblePetalPotNames(
            List<PetalMatcher.Token> tokens, int width, int height) {
        List<String> result = new ArrayList<>();
        for (PetalMatcher.Token token : tokens) {
            if (!isPetalLabel(token, width, height)) {
                continue;
            }
            String name = PostcardPotCatalog.canonicalName(token.text());
            if (name != null && !result.contains(name)) {
                result.add(name);
            }
        }
        return List.copyOf(result);
    }

    /** 花盆名稱只接受一個完整單列 OCR token，不合併任何左右或上下片段。 */
    private static List<PetalMatcher.Token> matchingTargetLabels(
            List<PetalMatcher.Token> tokens,
            String targetPotName,
            int width,
            int height) {
        String targetKey = normalizePetalPotName(targetPotName);
        List<PetalMatcher.Token> result = new ArrayList<>();
        if (targetKey.isEmpty()) {
            return result;
        }
        for (PetalMatcher.Token token : tokens) {
            if (!isPetalLabel(token, width, height)) {
                continue;
            }
            if (normalizePetalPotName(token.text()).equals(targetKey)) {
                result.add(token);
            }
        }
        return result;
    }

    /** 只校正單一花盆名稱 token 內的實機 OCR 字形誤識，不合併文字區塊。 */
    private static String normalizePetalPotName(String value) {
        String canonical = PostcardPotCatalog.canonicalName(value);
        return canonical == null ? "" : normalize(canonical);
    }

    private static boolean isPetalLabel(PetalMatcher.Token token, int width, int height) {
        String key = normalize(token.text());
        return token.text().matches(".*\\p{IsHan}.*")
                && token.centerX() > width * 0.03
                && token.centerX() < width * 0.97
                && token.centerY() > height * 0.53
                && token.centerY() < height * 0.96
                && !key.contains("選擇")
                && !key.equals("下一步")
                && !key.equals("花瓣");
    }

    private static boolean isDetailTitleCandidate(
            PetalMatcher.Token token, int width, int minY, int maxY) {
        return token.centerX() > width * 0.20
                && token.centerX() < width * 0.80
                && token.centerY() >= minY
                && token.centerY() <= maxY
                && isDetailTitleText(token.text());
    }

    private static boolean isDetailTitleText(String value) {
        String key = normalize(value);
        return value.matches(".*\\p{IsHan}.*")
                && !value.matches(".*\\d.*")
                && key.length() >= 3
                && key.length() <= 10
                && !key.contains("?前地")
                && !key.contains("?梁")
                && !key.contains("?縑?")
                && !key.contains("瘜冽?")
                && !key.contains("?亙?")
                && !key.contains("銝?")
                && !key.contains("再過")
                && !key.contains("會變回")
                && !key.contains("階段")
                && !key.contains("前往這裡");
    }

    private static boolean isDetailPostcardNameCandidate(
            PetalMatcher.Token token, int width, int minY, int maxY) {
        return token.centerX() > width * 0.18
                && token.centerX() < width * 0.82
                && token.centerY() >= minY
                && token.centerY() <= maxY
                && isDetailPostcardNameText(token.text());
    }

    private static boolean isDetailPostcardNameText(String value) {
        String key = normalize(value);
        return value.matches(".*\\p{L}.*")
                && key.length() >= 2
                && key.length() <= 30
                && !key.contains("花朵")
                && !key.contains("花瓣")
                && !key.contains("明信片")
                && !key.contains("目前地")
                && !key.contains("注意")
                && !key.contains("接受")
                && !key.contains("下一步")
                && !key.contains("再過")
                && !key.contains("會變回")
                && !key.contains("階段")
                && !key.contains("前往這裡")
                && !key.equals("tottori");
    }

    private static PetalMatcher.Token nearestCountAbove(
            List<PetalMatcher.Token> tokens,
            PetalMatcher.Token label,
            int width,
            int height) {
        PetalMatcher.Token best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PetalMatcher.Token token : tokens) {
            if (parseCount(token.text()) == null) {
                continue;
            }
            int dx = Math.abs(token.centerX() - label.centerX());
            int dy = label.top() - token.centerY();
            if (dx > width * 0.16 || dy < 0 || dy > height * 0.20) {
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

    private static boolean isPikminUiText(String value) {
        String key = normalize(value);
        return key.contains("選擇皮克敏")
                || key.equals("排序")
                || key.equals("自動")
                || key.equals("喜愛")
                || key.equals("取消")
                || key.equals("發現日")
                || key.equals("種類")
                || key.equals("友好度")
                || key.equals("飾品");
    }

    private static boolean hasText(List<PetalMatcher.Token> tokens, String text) {
        return findText(tokens, text) != null;
    }

    private static Target findExactText(List<PetalMatcher.Token> tokens, String text) {
        String key = normalize(text);
        return tokens.stream()
                .filter(token -> normalize(token.text()).equals(key))
                .findFirst()
                .map(PostcardMatcher::target)
                .orElse(null);
    }

    private static Target findText(List<PetalMatcher.Token> tokens, String text) {
        String key = normalize(text);
        for (PetalMatcher.Token token : tokens) {
            if (normalize(token.text()).contains(key)) {
                return target(token);
            }
        }
        for (PetalMatcher.Token first : tokens) {
            for (PetalMatcher.Token second : tokens) {
                int horizontalTolerance = Math.max(
                        first.right() - first.left(), second.right() - second.left()) * 2;
                int verticalTolerance = Math.max(
                        first.bottom() - first.top(), second.bottom() - second.top()) * 3;
                if (first == second
                        || second.top() < first.top()
                        || Math.abs(first.centerX() - second.centerX()) > horizontalTolerance
                        || second.top() - first.bottom() > verticalTolerance) {
                    continue;
                }
                if (normalize(first.text() + second.text()).contains(key)) {
                    return new Target(
                            first.text() + second.text(),
                            (Math.min(first.left(), second.left())
                                    + Math.max(first.right(), second.right())) / 2,
                            (first.top() + second.bottom()) / 2);
                }
            }
        }
        return null;
    }

    private static List<PetalMatcher.Token> exactTokens(
            List<PetalMatcher.Token> tokens, String... values) {
        List<String> keys = new ArrayList<>();
        for (String value : values) {
            keys.add(normalize(value));
        }
        List<PetalMatcher.Token> result = new ArrayList<>();
        for (PetalMatcher.Token token : tokens) {
            if (keys.contains(normalize(token.text()))) {
                result.add(token);
            }
        }
        return result;
    }

    private static Target target(PetalMatcher.Token token) {
        return new Target(token.text(), token.centerX(), token.centerY());
    }

    static String normalize(String value) {
        return TextNormalizer.normalizeForMatch(value);
    }

    /** Removes map-bubble navigation glyphs that OCR may merge into the location name. */
    static String normalizeLocationName(String value) {
        return normalize(value)
                .replaceAll("[<>‹›«»←→↑↓↗↘↙↖▶▷▸❯]+", "");
    }

    /**
     * 地點名稱可能由不同文字系統顯示，例如中文地圖氣泡與日文明信片標題。
     * 供「已點擊同一朵花、正在驗證詳細頁」的轉場使用；一般地圖候選仍須精確匹配。
     */
    static boolean usesDifferentWritingSystem(String first, String second) {
        return writingSystem(first) != writingSystem(second);
    }

    private static WritingSystem writingSystem(String value) {
        boolean han = false;
        boolean kana = false;
        boolean latin = false;
        boolean otherLetter = false;
        if (value != null) {
            for (int index = 0; index < value.length();) {
                int codePoint = value.codePointAt(index);
                index += Character.charCount(codePoint);
                if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                    han = true;
                } else if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HIRAGANA
                        || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.KATAKANA) {
                    kana = true;
                } else if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN) {
                    latin = true;
                } else if (Character.isLetter(codePoint)) {
                    otherLetter = true;
                }
            }
        }
        if (kana) {
            return WritingSystem.KANA;
        }
        if (han) {
            return WritingSystem.HAN;
        }
        if (latin) {
            return WritingSystem.LATIN;
        }
        return otherLetter ? WritingSystem.OTHER : WritingSystem.NONE;
    }

    private enum WritingSystem {
        NONE,
        HAN,
        KANA,
        LATIN,
        OTHER
    }
}
