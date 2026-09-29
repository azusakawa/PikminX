package com.pikminx.helper;

import static org.junit.Assert.*;
import org.junit.Test;

public final class SearchKeyboardGuardTest {
    private SearchKeyboardGuard.Evidence evidence(boolean ime, boolean page,
            boolean focused, boolean associated, String identity) {
        return new SearchKeyboardGuard.Evidence(true, ime, page, focused, associated, identity);
    }
    @Test public void requiresSameInputInTwoConsecutiveFramesAndSendsOnlyOnce() {
        SearchKeyboardGuard guard = new SearchKeyboardGuard(3);
        var e = evidence(true, true, true, true, "game:editor:ime");
        assertEquals(SearchKeyboardGuard.Action.WAIT, guard.observe(e));
        assertEquals(SearchKeyboardGuard.Action.SEND_BACK, guard.observe(e));
        assertTrue(guard.permitsDispatch(e));
        assertEquals(SearchKeyboardGuard.Action.WAIT, guard.observe(e));
        assertEquals(SearchKeyboardGuard.Action.WAIT, guard.observe(e));
        assertEquals(SearchKeyboardGuard.Action.FAIL_STUCK_KEYBOARD, guard.observe(e));
    }
    @Test public void rejectsFocusLossUnrelatedImeAndDifferentEditorAtDispatch() {
        for (var bad : new SearchKeyboardGuard.Evidence[]{
                evidence(true, true, false, true, "editor"),
                evidence(true, true, true, false, "editor"),
                evidence(true, false, true, true, "editor"),
                new SearchKeyboardGuard.Evidence(false, true, true, true, true, "editor")}) {
            SearchKeyboardGuard guard = new SearchKeyboardGuard(3);
            var good = evidence(true, true, true, true, "editor");
            guard.observe(good);
            guard.observe(good);
            assertFalse(guard.permitsDispatch(bad));
        }
        SearchKeyboardGuard guard = new SearchKeyboardGuard(3);
        guard.observe(evidence(true, true, true, true, "a"));
        assertEquals(SearchKeyboardGuard.Action.WAIT,
                guard.observe(evidence(true, true, true, true, "b")));
    }
    @Test public void unassociatedImeNeverAuthorizesBack() {
        SearchKeyboardGuard guard = new SearchKeyboardGuard(3);
        var bad = evidence(true, true, true, false, "editor");
        assertEquals(SearchKeyboardGuard.Action.WAIT, guard.observe(bad));
        assertEquals(SearchKeyboardGuard.Action.WAIT, guard.observe(bad));
        assertEquals(SearchKeyboardGuard.Action.FAIL_UNCONFIRMED_SEARCH, guard.observe(bad));
    }
    @Test public void keyboardAbsenceRequiresConfirmedPageAndTwoFrames() {
        SearchKeyboardGuard guard = new SearchKeyboardGuard(3);
        assertEquals(SearchKeyboardGuard.Action.WAIT,
                guard.observe(evidence(false, false, false, false, "")));
        var absent = evidence(false, true, false, false, "");
        assertEquals(SearchKeyboardGuard.Action.WAIT, guard.observe(absent));
        assertEquals(SearchKeyboardGuard.Action.COMPLETE, guard.observe(absent));
        guard.reset();
        assertEquals(SearchKeyboardGuard.Action.WAIT, guard.observe(absent));
    }
    @Test public void missingPageBreaksConsecutiveEvidence() {
        SearchKeyboardGuard guard = new SearchKeyboardGuard(3);
        var good = evidence(true, true, true, true, "editor");
        guard.observe(good);
        guard.observe(evidence(true, false, true, true, "editor"));
        assertEquals(SearchKeyboardGuard.Action.WAIT, guard.observe(good));
        assertEquals(SearchKeyboardGuard.Action.SEND_BACK, guard.observe(good));
        assertFalse(guard.permitsDispatch(evidence(true, true, true, true, "other")));
    }
}
