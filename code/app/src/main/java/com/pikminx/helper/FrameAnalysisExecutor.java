package com.pikminx.helper;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** One bounded shared executor for pure frame analysis; it never owns workflow state. */
final class FrameAnalysisExecutor implements AutoCloseable {
    static final int WORKER_COUNT = 1;
    static final int QUEUE_CAPACITY = 2;

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            WORKER_COUNT,
            WORKER_COUNT,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "pikminx-frame-analysis-" + THREAD_SEQUENCE.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    private volatile int highWater;

    boolean submit(Runnable task) {
        if (task == null || executor.isShutdown()) {
            return false;
        }
        try {
            executor.execute(task);
            recordQueueState();
            return true;
        } catch (RuntimeException rejected) {
            recordQueueState();
            return false;
        }
    }

    int depth() {
        return executor.getQueue().size();
    }

    int highWater() {
        return highWater;
    }

    private void recordQueueState() {
        int current = executor.getQueue().size();
        if (current > highWater) {
            highWater = current;
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
