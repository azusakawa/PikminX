package com.pikminx.helper;

/** One Back per keyboard session, only after stable evidence from the same focused input. */
final class SearchKeyboardGuard {
    enum Action {
        WAIT, SEND_BACK, COMPLETE, FAIL_UNCONFIRMED_SEARCH, FAIL_STUCK_KEYBOARD
    }

    record Evidence(boolean gameForeground, boolean inputMethodVisible,
            boolean searchPageConfirmed, boolean focusedSearchInput,
            boolean imeAssociated, String inputIdentity) {
        boolean permitsBack() {
            return gameForeground && inputMethodVisible && searchPageConfirmed
                    && focusedSearchInput && imeAssociated
                    && inputIdentity != null && !inputIdentity.isEmpty();
        }
    }

    private final int maximumVisibleFrames;
    private boolean backSent;
    private int absentFrames;
    private int unconfirmedFrames;
    private int visibleFramesAfterBack;
    private int confirmedFrames;
    private String confirmedInput = "";

    SearchKeyboardGuard(int maximumVisibleFrames) {
        this.maximumVisibleFrames = Math.max(2, maximumVisibleFrames);
    }

    Action observe(Evidence evidence) {
        if (!evidence.gameForeground() || !evidence.searchPageConfirmed()) {
            absentFrames = 0;
            confirmedFrames = 0;
            confirmedInput = "";
            return ++unconfirmedFrames >= maximumVisibleFrames
                    ? Action.FAIL_UNCONFIRMED_SEARCH : Action.WAIT;
        }
        if (!evidence.inputMethodVisible()) {
            unconfirmedFrames = 0;
            confirmedFrames = 0;
            visibleFramesAfterBack = 0;
            return ++absentFrames >= 2 ? Action.COMPLETE : Action.WAIT;
        }
        absentFrames = 0;
        if (backSent) {
            return ++visibleFramesAfterBack >= maximumVisibleFrames
                    ? Action.FAIL_STUCK_KEYBOARD : Action.WAIT;
        }
        if (!evidence.permitsBack()) {
            confirmedFrames = 0;
            confirmedInput = "";
            return ++unconfirmedFrames >= maximumVisibleFrames
                    ? Action.FAIL_UNCONFIRMED_SEARCH : Action.WAIT;
        }
        unconfirmedFrames = 0;
        if (!evidence.inputIdentity().equals(confirmedInput)) {
            confirmedInput = evidence.inputIdentity();
            confirmedFrames = 0;
        }
        if (++confirmedFrames < 2) {
            return Action.WAIT;
        }
        backSent = true;
        return Action.SEND_BACK;
    }

    /** Re-read live focus immediately before dispatch; a changed target must not receive Back. */
    boolean permitsDispatch(Evidence fresh) {
        return backSent && fresh.permitsBack()
                && confirmedInput.equals(fresh.inputIdentity());
    }

    void reset() {
        backSent = false;
        absentFrames = 0;
        unconfirmedFrames = 0;
        visibleFramesAfterBack = 0;
        confirmedFrames = 0;
        confirmedInput = "";
    }
}
