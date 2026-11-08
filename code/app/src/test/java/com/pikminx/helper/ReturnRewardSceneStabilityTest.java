package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;
import org.junit.Test;

public final class ReturnRewardSceneStabilityTest {
    private static final int WIDTH = 100;
    private static final int HEIGHT = 100;
    private static final ReturnRewardDetector.Region ROI =
            new ReturnRewardDetector.Region(20, 20, 80, 80);

    @Test
    public void firstObservationIsUnstableAndIdenticalFrameIsStable() {
        ReturnRewardSceneStability stability = new ReturnRewardSceneStability();
        int[] pixels = solid(0x406080);

        assertFalse(stability.observe(WIDTH, HEIGHT, frame(pixels), ROI));
        assertTrue(stability.observe(WIDTH, HEIGHT, frame(pixels), ROI));
    }

    @Test
    public void largeWholeRoiChangeIsUnstable() {
        ReturnRewardSceneStability stability = new ReturnRewardSceneStability();
        stability.observe(WIDTH, HEIGHT, frame(solid(0x202020)), ROI);
        assertFalse(stability.observe(WIDTH, HEIGHT, frame(solid(0x808080)), ROI));
    }

    @Test
    public void smallLocalizedChangeIsTolerable() {
        ReturnRewardSceneStability stability = new ReturnRewardSceneStability();
        int[] first = solid(0x406080);
        int[] second = Arrays.copyOf(first, first.length);
        second[45 * WIDTH + 45] = 0xFFFFFF;

        stability.observe(WIDTH, HEIGHT, frame(first), ROI);
        assertTrue(stability.observe(WIDTH, HEIGHT, frame(second), ROI));
    }

    @Test
    public void resetRequiresAnotherFirstObservation() {
        ReturnRewardSceneStability stability = new ReturnRewardSceneStability();
        IntBinaryOperator frame = frame(solid(0x406080));

        stability.observe(WIDTH, HEIGHT, frame, ROI);
        assertTrue(stability.observe(WIDTH, HEIGHT, frame, ROI));
        stability.reset();
        assertFalse(stability.observe(WIDTH, HEIGHT, frame, ROI));
    }

    @Test
    public void invalidInputIsSafeAndDoesNotReadPixels() {
        ReturnRewardSceneStability stability = new ReturnRewardSceneStability();
        AtomicInteger reads = new AtomicInteger();
        IntBinaryOperator pixels = (x, y) -> {
            reads.incrementAndGet();
            return 0;
        };

        assertFalse(stability.observe(0, HEIGHT, pixels, ROI));
        assertFalse(stability.observe(WIDTH, HEIGHT, pixels,
                new ReturnRewardDetector.Region(20, 20, 120, 80)));
        assertFalse(stability.observe(WIDTH, HEIGHT, pixels, null));
        assertFalse(stability.observe(WIDTH, HEIGHT, null, ROI));
        assertEquals(0, reads.get());
    }

    private static IntBinaryOperator frame(int[] pixels) {
        return (x, y) -> pixels[y * WIDTH + x];
    }

    private static int[] solid(int color) {
        int[] pixels = new int[WIDTH * HEIGHT];
        Arrays.fill(pixels, color);
        return pixels;
    }
}
