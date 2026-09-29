package com.pikminx.helper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 以穩定 OCR 簽章確認花盆清單已向下滾動到底。 */
final class PostcardPotScanGuard {
    enum Decision {
        CONTINUE,
        COMPLETE
    }

    private static final int REQUIRED_UNCHANGED_SCREENS = 2;
    private static final double SAME_SCREEN_MIN_JACCARD = 0.60d;
    private Set<String> lastNames = Set.of();
    private int unchangedScreens;
    private boolean complete;

    Decision observe(String signature) {
        return observe(signature == null || signature.isEmpty()
                ? List.of()
                : List.of(signature));
    }

    /**
     * 以可見完整單列花盆名稱的集合判斷是否仍是同一頁。
     *
     * <p>實機 OCR 在固定畫面仍可能把一個字辨成不同字，因此不要求整個字串完全
     * 相同；至少六成名稱重疊才算同頁。正常向下滾動只會保留少量相鄰列，不會誤判
     * 為到底。</p>
     */
    Decision observe(Collection<String> names) {
        if (complete) {
            return Decision.COMPLETE;
        }
        Set<String> currentNames = names == null
                ? Set.of()
                : new LinkedHashSet<>(names);
        if (!currentNames.isEmpty() && isSameScreen(lastNames, currentNames)) {
            unchangedScreens++;
            if (unchangedScreens >= REQUIRED_UNCHANGED_SCREENS) {
                complete = true;
                return Decision.COMPLETE;
            }
        } else {
            unchangedScreens = 0;
        }
        lastNames = Set.copyOf(currentNames);
        return Decision.CONTINUE;
    }

    private static boolean isSameScreen(Set<String> first, Set<String> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return false;
        }
        Set<String> intersection = new LinkedHashSet<>(first);
        intersection.retainAll(second);
        Set<String> union = new LinkedHashSet<>(first);
        union.addAll(second);
        return ((double) intersection.size() / union.size()) >= SAME_SCREEN_MIN_JACCARD;
    }

    void reset() {
        lastNames = Set.of();
        unchangedScreens = 0;
        complete = false;
    }
}
