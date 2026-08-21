package com.pikminx.helper;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public final class PostcardPotScanGuardTest {
    @Test
    public void completesOnlyAfterTheBottomSignatureIsRepeatedTwice() {
        PostcardPotScanGuard guard = new PostcardPotScanGuard();

        assertEquals(PostcardPotScanGuard.Decision.CONTINUE, guard.observe("top"));
        assertEquals(PostcardPotScanGuard.Decision.CONTINUE, guard.observe("middle"));
        assertEquals(PostcardPotScanGuard.Decision.CONTINUE, guard.observe("bottom"));
        assertEquals(PostcardPotScanGuard.Decision.CONTINUE, guard.observe("bottom"));
        assertEquals(PostcardPotScanGuard.Decision.COMPLETE, guard.observe("bottom"));
        assertEquals(PostcardPotScanGuard.Decision.COMPLETE, guard.observe("bottom"));
    }

    @Test
    public void resetStartsANewDownwardScan() {
        PostcardPotScanGuard guard = new PostcardPotScanGuard();
        guard.observe("bottom");
        guard.observe("bottom");
        guard.observe("bottom");

        guard.reset();

        assertEquals(PostcardPotScanGuard.Decision.CONTINUE, guard.observe("bottom"));
    }

    @Test
    public void toleratesOneOcrNameChangingOnTheSameBottomScreen() {
        PostcardPotScanGuard guard = new PostcardPotScanGuard();

        assertEquals(PostcardPotScanGuard.Decision.CONTINUE, guard.observe(List.of(
                "紅色鼠尾草", "藍色鼠尾草", "黃色鸚鵡鬱金香", "紅色鸚鵡鬱金香")));
        assertEquals(PostcardPotScanGuard.Decision.CONTINUE, guard.observe(List.of(
                "紅色鼠尾草", "藍色鼠尾草", "黃色鵬講鬱金香", "紅色鸚鵡鬱金香")));
        assertEquals(PostcardPotScanGuard.Decision.COMPLETE, guard.observe(List.of(
                "紅色鼠尾草", "藍色鼠尾草", "黃色鸚鵡鬱金香", "紅色鸚鵡鬱金香")));
    }

    @Test
    public void overlappingAdjacentRowsDoNotLookLikeTheSameScreen() {
        PostcardPotScanGuard guard = new PostcardPotScanGuard();

        assertEquals(PostcardPotScanGuard.Decision.CONTINUE,
                guard.observe(List.of("白色花瓣", "黃色花瓣", "紅色花瓣", "藍色花瓣")));
        assertEquals(PostcardPotScanGuard.Decision.CONTINUE,
                guard.observe(List.of("紅色花瓣", "藍色花瓣", "白色玫瑰", "黃色玫瑰")));
        assertEquals(PostcardPotScanGuard.Decision.CONTINUE,
                guard.observe(List.of("白色玫瑰", "黃色玫瑰", "紅色玫瑰", "藍色玫瑰")));
    }
}
