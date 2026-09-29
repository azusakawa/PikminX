package com.pikminx.helper;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public final class FrameAnalysisExecutorTest {
    @Test
    public void usesBoundedQueueAndReportsHighWater() throws Exception {
        FrameAnalysisExecutor executor = new FrameAnalysisExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            assertTrue(executor.submit(() -> {
                started.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(executor.submit(() -> { }));
            assertTrue(executor.submit(() -> { }));
            assertTrue(executor.depth() <= FrameAnalysisExecutor.QUEUE_CAPACITY);
            assertTrue(executor.highWater() <= FrameAnalysisExecutor.QUEUE_CAPACITY);
        } finally {
            release.countDown();
            executor.close();
        }
    }
}
