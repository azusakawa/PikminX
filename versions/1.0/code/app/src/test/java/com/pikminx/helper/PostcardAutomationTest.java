package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PostcardAutomationTest {
    @Test
    public void countsOnlyAfterReceiptPageHasExited() {
        PostcardAutomation automation = new PostcardAutomation();
        automation.start(2, "黃色花瓣", 1);

        automation.markReceiveTapped();
        assertEquals(0, automation.completedCount());
        assertEquals(PostcardAutomation.Step.WAIT_RECEIPT_EXIT, automation.step());
        assertTrue(automation.confirmReceiptExit());
        assertEquals(1, automation.completedCount());
        assertFalse(automation.isComplete());
        automation.markReceiveTapped();
        assertTrue(automation.confirmReceiptExit());
        assertEquals(2, automation.completedCount());
        assertTrue(automation.isComplete());
        assertFalse(automation.confirmReceiptExit());
    }

    @Test
    public void hardCapsCollectionAtFifteen() {
        PostcardAutomation automation = new PostcardAutomation();
        automation.start(99, "黃色花瓣", 99);
        assertEquals(15, automation.collectionLimit());
        assertEquals("黃色花瓣", automation.petalPotName());
        assertEquals(5, automation.pikminCount());
    }

    @Test
    public void usesTheConfiguredPetalPotNameAsItsOnlyPetalTarget() {
        PostcardAutomation automation = new PostcardAutomation();
        automation.start(2, "  紅色天堂鳥  ", 1);

        assertEquals("紅色天堂鳥", automation.petalPotName());
    }
}
