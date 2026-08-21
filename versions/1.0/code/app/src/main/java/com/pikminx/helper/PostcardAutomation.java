package com.pikminx.helper;

/** 保存單次明信片工作階段；Android 手勢與 OCR 保留在 AccessibilityService。 */
final class PostcardAutomation {
    enum Step {
        FIND_FLOWER,
        OPEN_FLOWER,
        USE_PETALS,
        ACCEPT_WARNING,
        OPEN_PETAL_SEARCH,
        ENTER_PETAL_SEARCH,
        CLOSE_PETAL_KEYBOARD,
        SELECT_PETAL,
        TAP_NEXT,
        NEXT,
        OPEN_SORT,
        CHOOSE_FAVORITE,
        SELECT_PIKMIN,
        GO,
        RECEIVE,
        WAIT_RECEIPT_EXIT
    }

    private int collectionLimit;
    private int completedCount;
    private String petalPotName = "";
    private int pikminCount = 1;
    private Step step = Step.FIND_FLOWER;
    private boolean favoriteApplied;
    private boolean receiveTapped;

    void start(int requestedLimit, String requestedPetalPotName, int requestedPikminCount) {
        collectionLimit = Math.max(1, Math.min(15, requestedLimit));
        completedCount = 0;
        petalPotName = requestedPetalPotName == null ? "" : requestedPetalPotName.trim();
        pikminCount = Math.max(1, Math.min(5, requestedPikminCount));
        step = Step.FIND_FLOWER;
        favoriteApplied = false;
        receiveTapped = false;
    }

    int collectionLimit() {
        return collectionLimit;
    }

    int completedCount() {
        return completedCount;
    }

    boolean isComplete() {
        return completedCount >= collectionLimit;
    }

    Step step() {
        return step;
    }

    void moveTo(Step next) {
        step = next;
    }

    String petalPotName() {
        return petalPotName;
    }

    int pikminCount() {
        return pikminCount;
    }

    boolean favoriteApplied() {
        return favoriteApplied;
    }

    void markFavoriteApplied() {
        favoriteApplied = true;
    }

    boolean receiveTapped() {
        return receiveTapped;
    }

    void markReceiveTapped() {
        receiveTapped = true;
        step = Step.WAIT_RECEIPT_EXIT;
    }

    void retryReceive() {
        receiveTapped = false;
        step = Step.RECEIVE;
    }

    /** 只有 OCR 已確認離開接收畫面後，才把本輪計入完成數。 */
    boolean confirmReceiptExit() {
        if (!receiveTapped) {
            return false;
        }
        receiveTapped = false;
        completedCount++;
        favoriteApplied = false;
        step = Step.FIND_FLOWER;
        return true;
    }
}
