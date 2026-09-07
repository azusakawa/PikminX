package com.pikminx.helper;

import java.util.ArrayList;
import java.util.List;

/** 餵食頁的 OCR 數量與餵食後花朵／發光變化判斷。 */
final class FeedScreenAnalyzer {
    interface PixelReader {
        int get(int x, int y);
    }

    record NectarSelection(
            String name, int count, int petalCount, int x, int y, int tapY) {}

    record NectarSearchAnalysis(
            NectarSelection selection,
            String reason,
            boolean nectarCountFound,
            boolean petalCountFound) {}

    record VisualSignature(int columns, int rows, int[] pixels) {}

    record ZoomOutPinch(
            int leftStartX,
            int leftEndX,
            int rightStartX,
            int rightEndX,
            int y) {}

    record BloomTarget(int x, int y, int score) {}

    record SpiralPoint(int x, int y) {}

    record NectarHoldProgress(Integer count, int stableReads, boolean shouldRelease) {}

    record FeedRoundStatistics(int nectarConsumed, int petalsObserved) {}

    enum NoEffectAction { WAIT, RETRY, GIVE_UP }

    private FeedScreenAnalyzer() {}

    static ZoomOutPinch zoomOutPinch(int width, int height) {
        return new ZoomOutPinch(
                Math.round(width * 0.20f),
                Math.round(width * 0.43f),
                Math.round(width * 0.80f),
                Math.round(width * 0.57f),
                Math.round(height * 0.50f));
    }

    static boolean isPikminDetailOpen(List<PetalMatcher.Token> tokens) {
        boolean friendship = false;
        boolean collected = false;
        boolean petals = false;
        for (PetalMatcher.Token token : tokens) {
            String text = PetalMatcher.normalize(token.text());
            friendship |= text.contains("友好度");
            collected |= text.contains("收集");
            petals |= text.contains("花瓣");
        }
        return friendship && collected && petals;
    }

    static boolean isSharePreviewOpen(List<PetalMatcher.Token> tokens) {
        boolean share = false;
        boolean save = false;
        for (PetalMatcher.Token token : tokens) {
            String text = PetalMatcher.normalize(token.text());
            share |= text.contains("分享");
            save |= text.contains("儲存") || text.contains("保存");
        }
        return share && save;
    }

    static boolean isFeedViewReadyAfterNectarSelection(
            boolean panelOpen, boolean detailOpen, Integer nectarCount) {
        return !panelOpen && !detailOpen && nectarCount != null;
    }

    static Integer currentNectarCount(
            List<PetalMatcher.Token> tokens, int width, int height) {
        PetalMatcher.Token best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PetalMatcher.Token token : tokens) {
            Integer value = number(token.text());
            if (value == null
                    || token.centerX() < width * 0.34f
                    || token.centerX() > width * 0.70f
                    || token.centerY() < height * 0.78f
                    || token.centerY() > height * 0.98f) {
                continue;
            }
            double dx = token.centerX() - width * 0.52;
            double dy = token.centerY() - height * 0.90;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = token;
                bestDistance = distance;
            }
        }
        return best == null ? null : number(best.text());
    }

    static boolean hasReachedPetalLimit(int current, int effectiveLimit) {
        return current >= effectiveLimit;
    }

    /** 精華為 0 時已無法餵食；其他數量維持「低於門檻才切換」的既有語意。 */
    static boolean shouldAdvanceNectar(int remaining, int minimumThreshold) {
        return remaining <= 0
                || SwitchGuard.isBelowThreshold(remaining, minimumThreshold);
    }

    /** 讀取畫面下方遊戲回執，例如「紅色花瓣 +8」；不接受詳情卡的花瓣文字。 */
    static Integer petalReceiptGain(
            List<PetalMatcher.Token> tokens, int width, int height) {
        for (PetalMatcher.Token label : tokens) {
            if (!isPetalReceiptToken(label, height)
                    || !receiptText(label.text()).contains("花瓣")) {
                continue;
            }
            Integer inline = receiptNumber(label.text());
            if (inline != null) {
                return inline;
            }
            for (PetalMatcher.Token amount : tokens) {
                if (amount == label
                        || !isPetalReceiptToken(amount, height)
                        || Math.abs(amount.centerX() - label.centerX()) > width * 0.25f
                        || Math.abs(amount.centerY() - label.centerY()) > height * 0.06f) {
                    continue;
                }
                Integer split = receiptNumber(amount.text());
                if (split != null) {
                    return split;
                }
            }
        }
        return null;
    }

    /** 同一回執連續出現在多張畫面只計一次；數字改變或消失後重現視為新回執。 */
    static int newPetalReceiptGain(
            Integer currentGain, Integer previousGain, boolean receiptVisible) {
        return currentGain != null
                && (!receiptVisible || !currentGain.equals(previousGain))
                ? currentGain
                : 0;
    }

    static NectarHoldProgress observeNectarHold(
            Integer previousCount,
            int previousStableReads,
            Integer currentCount,
            long elapsedMillis,
            long minimumMillis,
            long maximumMillis,
            int requiredStableReads) {
        if (currentCount == null) {
            return new NectarHoldProgress(
                    previousCount,
                    previousStableReads,
                    elapsedMillis >= maximumMillis);
        }
        int stableReads = currentCount.equals(previousCount)
                ? previousStableReads + 1 : 1;
        boolean stable = elapsedMillis >= minimumMillis
                && stableReads >= Math.max(1, requiredStableReads);
        return new NectarHoldProgress(
                currentCount,
                stableReads,
                stable || elapsedMillis >= maximumMillis);
    }

    static List<SpiralPoint> ellipticalSpiral(int width, int height, int segments) {
        if (width <= 0 || height <= 0 || segments <= 0) {
            return List.of();
        }
        int centerX = Math.round(width * 0.50f);
        int centerY = Math.round(height * 0.54f);
        float radiusX = width * 0.36f;
        float radiusY = height * 0.25f;
        List<SpiralPoint> points = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            float progress = index / (float) segments;
            double angle = Math.PI * 10.0 * progress;
            points.add(new SpiralPoint(
                    Math.round(centerX + radiusX * progress * (float) Math.cos(angle)),
                    Math.round(centerY + radiusY * progress * (float) Math.sin(angle))));
        }
        return List.copyOf(points);
    }

    static List<SpiralPoint> ellipticalSpiralFromTarget(
            int width, int height, int segments, BloomTarget target) {
        if (target == null
                || target.x() < 0 || target.x() >= width
                || target.y() < 0 || target.y() >= height) {
            return List.of();
        }
        List<SpiralPoint> points = new ArrayList<>(ellipticalSpiral(width, height, segments));
        if (points.isEmpty()) {
            return List.of();
        }
        points.set(0, new SpiralPoint(target.x(), target.y()));
        return List.copyOf(points);
    }

    static FeedRoundStatistics feedRoundStatistics(
            int nectarBefore, int nectarAfter, int petalsObserved) {
        Integer consumed = consumedNectar(nectarBefore, nectarAfter);
        return consumed == null ? null
                : new FeedRoundStatistics(consumed, Math.max(0, petalsObserved));
    }

    static boolean isNectarPanelOpen(int width, int height, PixelReader pixelAt) {
        int light = 0;
        int total = 0;
        for (int row = 0; row < 5; row++) {
            int y = Math.round(height * (0.12f + row * 0.025f));
            for (int column = 0; column < 9; column++) {
                int x = Math.round(width * (0.10f + column * 0.10f));
                int color = pixelAt.get(x, y);
                int red = (color >> 16) & 0xff;
                int green = (color >> 8) & 0xff;
                int blue = color & 0xff;
                int minimum = Math.min(red, Math.min(green, blue));
                int maximum = Math.max(red, Math.max(green, blue));
                if (minimum >= 220 && maximum - minimum <= 40) {
                    light++;
                }
                total++;
            }
        }
        return light >= Math.round(total * 0.70f);
    }

    /** 回傳同一精華餵食前後的實際消耗量；數量反向增加時等待下一次 OCR。 */
    static Integer consumedNectar(int before, int after) {
        return before >= 0 && after >= 0 && after <= before ? before - after : null;
    }

    static NectarSelection findNectar(
            List<PetalMatcher.Token> tokens, String target, int width, int height) {
        PetalMatcher.Selection sharedMatch = PetalMatcher.findFlower(
                tokens, target, width, height);
        if (sharedMatch != null) {
            return nectarSelection(
                    tokens,
                    nectarDisplayName(target),
                    sharedMatch.count(),
                    sharedMatch.x(),
                    sharedMatch.y(),
                    sharedMatch.tapY(),
                    width,
                    height);
        }
        String targetKey = nectarKey(target);
        if (targetKey.isEmpty()) {
            return null;
        }
        PetalMatcher.Token label = null;
        for (PetalMatcher.Token token : tokens) {
            if (token.centerY() >= height * 0.15f
                    && token.centerY() <= height * 0.92f
                    && nectarKey(token.text()).equals(targetKey)) {
                label = token;
                break;
            }
        }
        if (label == null) {
            return null;
        }
        PetalMatcher.Token count = nearestNumber(tokens, label, width, height);
        Integer amount = count == null ? null : number(count.text());
        return amount == null ? null : nectarSelection(
                tokens,
                nectarDisplayName(target),
                amount,
                label.centerX(),
                label.centerY(),
                nectarTapY(label, height),
                width,
                height);
    }

    /** 搜尋完成後：主色使用左上第一張；活動精華誤識時只接受唯一可見結果。 */
    static NectarSelection findSearchedNectar(
            List<PetalMatcher.Token> tokens, String target, int width, int height) {
        return analyzeSearchedNectar(tokens, target, width, height).selection();
    }

    /** 回傳精華卡片及不含 OCR 原文／庫存值的診斷結果。 */
    static NectarSearchAnalysis analyzeSearchedNectar(
            List<PetalMatcher.Token> tokens, String target, int width, int height) {
        String canonical = PetalCatalog.canonicalName(target);
        boolean baseNectar = canonical != null && canonical.endsWith("花瓣");
        if (!baseNectar) {
            NectarSelection exact = findNectar(tokens, target, width, height);
            if (exact != null || canonical == null) {
                return new NectarSearchAnalysis(
                        exact,
                        exact == null ? "invalid-target" : "matched",
                        exact != null,
                        exact != null);
            }
            PetalPotDetector.Match match = PetalPotDetector.findSingleVisible(
                    tokens,
                    0,
                    width,
                    height,
                    0.34f,
                    0.48f,
                    0.16f,
                    0.10f,
                    value -> value != null
                            && value.indexOf('\n') < 0
                            && value.indexOf('\r') < 0
                            && value.codePoints().filter(Character::isLetter).count() >= 2);
            NectarSelection selection = match == null
                    ? null
                    : nectarSelection(
                    tokens,
                    canonical,
                    match.count(),
                    match.x(),
                    match.labelY(),
                    Math.max(0, match.labelTop() - Math.round(height * 0.075f)),
                    width,
                    height);
            return new NectarSearchAnalysis(
                    selection,
                    selection == null ? "flower-card-missing" : "matched",
                    match != null,
                    selection != null);
        }
        PetalMatcher.Token nectarToken = nearestNumberInRegion(
                tokens, width, height,
                0.04f, 0.31f, 0.32f, 0.43f,
                0.16f, 0.36f,
                FeedScreenAnalyzer::cardNumber);
        PetalMatcher.Token petalToken = nearestNumberInRegion(
                tokens, width, height,
                0.04f, 0.31f, 0.22f, 0.31f,
                0.16f, 0.27f,
                FeedScreenAnalyzer::number);
        Integer nectarCount = nectarToken == null ? null : cardNumber(nectarToken.text());
        Integer petalCount = petalToken == null ? null : number(petalToken.text());
        if (nectarCount == null || petalCount == null) {
            String reason = nectarCount == null && petalCount == null
                    ? "base-both-counts-missing"
                    : nectarCount == null
                            ? "base-nectar-count-missing"
                            : "base-petal-count-missing";
            return new NectarSearchAnalysis(
                    null, reason, nectarCount != null, petalCount != null);
        }
        return new NectarSearchAnalysis(
                new NectarSelection(
                        nectarDisplayName(target),
                        nectarCount,
                        petalCount,
                        Math.round(width * 0.16f),
                        Math.round(height * 0.39f),
                        Math.round(height * 0.32f)),
                "matched",
                true,
                true);
    }

    private static PetalMatcher.Token nearestNumberInRegion(
            List<PetalMatcher.Token> tokens,
            int width,
            int height,
            float minimumX,
            float maximumX,
            float minimumY,
            float maximumY,
            float expectedX,
            float expectedY,
            java.util.function.Function<String, Integer> parser) {
        PetalMatcher.Token best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PetalMatcher.Token token : tokens) {
            if (parser.apply(token.text()) == null
                    || token.centerX() < width * minimumX
                    || token.centerX() > width * maximumX
                    || token.centerY() < height * minimumY
                    || token.centerY() > height * maximumY) {
                continue;
            }
            double dx = token.centerX() - width * expectedX;
            double dy = token.centerY() - height * expectedY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = token;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static NectarSelection nectarSelection(
            List<PetalMatcher.Token> tokens,
            String name,
            int nectarCount,
            int x,
            int y,
            int tapY,
            int width,
            int height) {
        Integer petalCount = petalStockForCard(tokens, x, y, width, height);
        return petalCount == null ? null
                : new NectarSelection(name, nectarCount, petalCount, x, y, tapY);
    }

    /** 只取目標卡片同欄、精華球上方持續顯示的花瓣庫存。 */
    private static Integer petalStockForCard(
            List<PetalMatcher.Token> tokens,
            int cardX,
            int labelY,
            int width,
            int height) {
        return PetalPotDetector.findCardNumberAbove(
                tokens,
                cardX,
                labelY,
                0,
                width,
                height,
                0.09f,
                0.08f,
                0.20f,
                0.13f,
                FeedScreenAnalyzer::number);
    }

    static VisualSignature capture(int width, int height, PixelReader pixelAt) {
        int columns = 72;
        int rows = 72;
        int[] pixels = new int[columns * rows];
        for (int row = 0; row < rows; row++) {
            int y = Math.min(height - 1,
                    Math.round(height * (0.20f + 0.58f * row / (rows - 1f))));
            for (int column = 0; column < columns; column++) {
                int x = Math.min(width - 1,
                        Math.round(width * (0.10f + 0.80f * column / (columns - 1f))));
                pixels[row * columns + column] = pixelAt.get(x, y);
            }
        }
        return new VisualSignature(columns, rows, pixels);
    }

    static boolean hasNewBloomEffect(
            VisualSignature before, int width, int height, PixelReader pixelAt) {
        if (before == null || before.pixels().length != before.columns() * before.rows()) {
            return false;
        }
        int changedGlowPixels = 0;
        int changedScenePixels = 0;
        int[] changedSceneTiles = new int[16];
        for (int row = 0; row < before.rows(); row++) {
            int y = Math.min(height - 1,
                    Math.round(height * (0.20f + 0.58f * row / (before.rows() - 1f))));
            for (int column = 0; column < before.columns(); column++) {
                int x = Math.min(width - 1,
                        Math.round(width * (0.10f + 0.80f * column / (before.columns() - 1f))));
                int previous = before.pixels()[row * before.columns() + column];
                int current = pixelAt.get(x, y);
                if (isNewGlow(previous, current)) {
                    changedGlowPixels++;
                }
                if (isSceneChange(previous, current)) {
                    changedScenePixels++;
                    changedSceneTiles[sceneTile(row, column, before.rows(), before.columns())]++;
                }
            }
        }
        return changedGlowPixels >= Math.max(18, before.pixels().length / 240)
                && !isDiffuseSceneChange(
                        changedScenePixels, before.pixels().length, changedSceneTiles);
    }

    /** 從餵食前後影像差異挑出發光花朵中心，避免以滑動手勢拖曳遊戲鏡頭。 */
    static List<BloomTarget> findBloomTargets(
            VisualSignature before, int width, int height, PixelReader pixelAt) {
        if (before == null || before.pixels().length != before.columns() * before.rows()) {
            return List.of();
        }
        List<BloomTarget> raw = new ArrayList<>();
        int changedScenePixels = 0;
        int[] changedSceneTiles = new int[16];
        for (int row = 0; row < before.rows(); row++) {
            int y = Math.min(height - 1,
                    Math.round(height * (0.20f + 0.58f * row / (before.rows() - 1f))));
            for (int column = 0; column < before.columns(); column++) {
                int x = Math.min(width - 1,
                        Math.round(width * (0.10f + 0.80f * column / (before.columns() - 1f))));
                int previous = before.pixels()[row * before.columns() + column];
                int current = pixelAt.get(x, y);
                if (isSceneChange(previous, current)) {
                    changedScenePixels++;
                    changedSceneTiles[sceneTile(row, column, before.rows(), before.columns())]++;
                }
                if (isNewGlow(previous, current)) {
                    raw.add(new BloomTarget(
                            x,
                            y,
                            maximum(current) - maximum(previous)));
                }
            }
        }
        if (isDiffuseSceneChange(
                changedScenePixels, before.pixels().length, changedSceneTiles)) {
            return List.of();
        }
        raw.sort((left, right) -> Integer.compare(right.score(), left.score()));
        List<BloomTarget> targets = new ArrayList<>();
        int minimumXDistance = Math.max(12, Math.round(width * 0.045f));
        int minimumYDistance = Math.max(12, Math.round(height * 0.035f));
        for (BloomTarget candidate : raw) {
            boolean overlaps = false;
            for (BloomTarget accepted : targets) {
                if (Math.abs(candidate.x() - accepted.x()) <= minimumXDistance
                        && Math.abs(candidate.y() - accepted.y()) <= minimumYDistance) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                targets.add(candidate);
            }
        }
        return List.copyOf(targets);
    }

    static boolean isSameBloomTarget(
            BloomTarget first, BloomTarget second, int width, int height) {
        return first != null
                && second != null
                && Math.abs(first.x() - second.x()) <= Math.max(12, width * 0.05f)
                && Math.abs(first.y() - second.y()) <= Math.max(12, height * 0.04f);
    }

    static BloomTarget nearestBloomTargetToCenter(
            List<BloomTarget> targets, int width, int height) {
        if (targets == null || targets.isEmpty() || width <= 0 || height <= 0) {
            return null;
        }
        int centerX = Math.round(width * 0.50f);
        int centerY = Math.round(height * 0.54f);
        BloomTarget nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (BloomTarget target : targets) {
            long dx = target.x() - centerX;
            long dy = target.y() - centerY;
            long distance = dx * dx + dy * dy;
            if (nearest == null
                    || distance < nearestDistance
                    || (distance == nearestDistance && target.score() > nearest.score())) {
                nearest = target;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    static BloomTarget nextUnvisitedBloomTarget(
            List<BloomTarget> targets,
            List<BloomTarget> visited,
            int width,
            int height) {
        for (BloomTarget target : targets) {
            boolean alreadyVisited = visited.stream().anyMatch(previous ->
                    isSameBloomTarget(previous, target, width, height));
            if (!alreadyVisited) {
                return target;
            }
        }
        return null;
    }

    static boolean hasStableBloom(int confirmationFrames, int requiredFrames) {
        return confirmationFrames >= requiredFrames;
    }

    static boolean hasStableSearchEvidence(
            boolean searchOpen,
            boolean queryMatches,
            int confirmationFrames,
            int requiredFrames) {
        return searchOpen && queryMatches && confirmationFrames >= requiredFrames;
    }

    /** 同一張精華卡片需連續穩定出現，避免 OCR 轉場期間點到相鄰結果。 */
    static boolean isSameNectarSelection(
            NectarSelection previous,
            NectarSelection current,
            int width,
            int height) {
        return "stable".equals(nectarSelectionStabilityReason(
                previous, current, width, height));
    }

    static String nectarSelectionStabilityReason(
            NectarSelection previous,
            NectarSelection current,
            int width,
            int height) {
        if (previous == null || current == null) {
            return "first-frame";
        }
        int maximumXDrift = Math.max(8, Math.round(width * 0.03f));
        int maximumYDrift = Math.max(8, Math.round(height * 0.03f));
        if (!previous.name().equals(current.name())) {
            return "target-changed";
        }
        if (previous.count() != current.count()
                && previous.petalCount() != current.petalCount()) {
            return "both-counts-changed";
        }
        if (previous.count() != current.count()) {
            return "nectar-count-changed";
        }
        if (previous.petalCount() != current.petalCount()) {
            return "petal-count-changed";
        }
        if (Math.abs(previous.x() - current.x()) > maximumXDrift
                || Math.abs(previous.tapY() - current.tapY()) > maximumYDrift) {
            return "card-position-changed";
        }
        return "stable";
    }

    static NoEffectAction noEffectAction(
            int gestureCount, int maxGestures, long elapsedMillis, long timeoutMillis) {
        if (elapsedMillis < timeoutMillis) {
            return NoEffectAction.WAIT;
        }
        return gestureCount >= maxGestures ? NoEffectAction.GIVE_UP : NoEffectAction.RETRY;
    }

    private static PetalMatcher.Token nearestNumber(
            List<PetalMatcher.Token> tokens,
            PetalMatcher.Token label,
            int width,
            int height) {
        PetalMatcher.Token best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PetalMatcher.Token token : tokens) {
            if (number(token.text()) == null) {
                continue;
            }
            int dx = token.centerX() - label.centerX();
            int dy = token.centerY() - label.centerY();
            if (Math.abs(dx) > width * 0.16f || Math.abs(dy) > height * 0.12f) {
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

    private static String nectarKey(String value) {
        String canonical = PetalCatalog.canonicalName(value);
        String normalized = PetalMatcher.normalize(canonical == null ? value : canonical);
        return normalized.replace("花瓣", "").replace("精華", "");
    }

    static String nectarDisplayName(String value) {
        String canonical = PetalCatalog.canonicalName(value);
        if (canonical != null && canonical.endsWith("花瓣")) {
            return nectarKey(canonical) + "精華";
        }
        return canonical == null ? value : canonical;
    }

    /** 基礎精華只搜尋顏色；活動精華沿用花盆要求的「顏色 花名」。 */
    static String nectarSearchQuery(String value) {
        String canonical = PetalCatalog.canonicalName(value);
        PostcardPotCatalog.Color color = PostcardPotCatalog.colorOf(canonical);
        if (canonical == null || color == null) {
            return "";
        }
        return canonical.endsWith("花瓣")
                ? color.label()
                : PostcardPotCatalog.searchQuery(canonical);
    }

    private static int nectarTapY(PetalMatcher.Token label, int height) {
        return Math.max(0, label.top() - Math.round(height * 0.075f));
    }

    private static Integer number(String value) {
        String normalized = java.text.Normalizer.normalize(
                        value == null ? "" : value, java.text.Normalizer.Form.NFKC)
                .replaceAll("[,，.·'\\s]", "")
                .replace('O', '0')
                .replace('o', '0')
                .replace('I', '1')
                .replace('l', '1')
                .replace('|', '1');
        if (!normalized.matches("[0-9]{1,4}")) {
            return null;
        }
        int result = Integer.parseInt(normalized);
        return result <= 1200 ? result : null;
    }

    private static boolean isPetalReceiptToken(PetalMatcher.Token token, int height) {
        return token.centerY() >= height * 0.68f && token.centerY() <= height * 0.92f;
    }

    private static Integer receiptNumber(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\+([0-9OoIl|]{1,3})")
                .matcher(receiptText(value));
        if (!matcher.find()) {
            return null;
        }
        Integer gain = number(matcher.group(1));
        return gain != null && gain > 0 ? gain : null;
    }

    private static String receiptText(String value) {
        return java.text.Normalizer.normalize(
                        value == null ? "" : value, java.text.Normalizer.Form.NFKC)
                .replaceAll("\\s", "");
    }

    /** 第一格數量偶爾會被辨識成「279 +」；只在搜尋結果卡片放寬此格式。 */
    private static Integer cardNumber(String value) {
        String normalized = java.text.Normalizer.normalize(
                value == null ? "" : value, java.text.Normalizer.Form.NFKC);
        return number(normalized.replaceFirst("\\s*\\+$", ""));
    }

    private static boolean isNewGlow(int before, int after) {
        int beforeMax = maximum(before);
        int afterRed = (after >> 16) & 0xff;
        int afterGreen = (after >> 8) & 0xff;
        int afterBlue = after & 0xff;
        int afterMax = Math.max(afterRed, Math.max(afterGreen, afterBlue));
        int afterMin = Math.min(afterRed, Math.min(afterGreen, afterBlue));
        boolean brightWhite = afterMin >= 205;
        boolean brightColor = afterMax >= 225 && afterMax - afterMin >= 55;
        return (brightWhite || brightColor) && beforeMax <= afterMax - 45;
    }

    private static boolean isSceneChange(int before, int after) {
        int red = Math.abs(((before >> 16) & 0xff) - ((after >> 16) & 0xff));
        int green = Math.abs(((before >> 8) & 0xff) - ((after >> 8) & 0xff));
        int blue = Math.abs((before & 0xff) - (after & 0xff));
        return red + green + blue >= 90;
    }

    private static int sceneTile(int row, int column, int rows, int columns) {
        return row * 4 / rows * 4 + column * 4 / columns;
    }

    private static boolean isDiffuseSceneChange(
            int changedPixels, int totalPixels, int[] changedTiles) {
        if (changedPixels <= totalPixels / 5) {
            return false;
        }
        int changedTileCount = 0;
        int pixelsPerTile = totalPixels / changedTiles.length;
        for (int changedTile : changedTiles) {
            if (changedTile > pixelsPerTile / 5) {
                changedTileCount++;
            }
        }
        return changedTileCount >= 12;
    }

    private static int maximum(int color) {
        return Math.max((color >> 16) & 0xff,
                Math.max((color >> 8) & 0xff, color & 0xff));
    }
}
