package me.sshcrack.mc_talking.deepseek;

import me.sshcrack.mc_talking.McTalking;

import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared FCFS queue for all DeepSeek speech-processing tasks.
 *
 * <p>Limits concurrent processing to {@value #MAX_CONCURRENT} threads to keep
 * CPU/RAM usage bounded regardless of how many citizens are in a conversation.
 * Excess tasks wait in an unbounded {@link LinkedBlockingQueue} and are picked
 * up in first-come-first-served order as slots free.</p>
 *
 * <p>All work submitted here runs on daemon background threads — it never blocks
 * the Minecraft server thread.</p>
 */
public class DeepSeekRequestQueue {
    public static final int MAX_CONCURRENT = 2;

    private static final AtomicInteger threadCounter = new AtomicInteger(0);
    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            MAX_CONCURRENT,
            MAX_CONCURRENT,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "deepseek-queue-" + threadCounter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            }
    );

    /**
     * Submits a task to the shared queue.
     *
     * @param task the work to execute (STT + DeepSeek HTTP call + chat reply)
     * @return a {@link Future} that can be used to cancel the task if it has not yet started
     */
    public static Future<?> submit(Runnable task) {
        int queuedBefore = executor.getQueue().size();
        int activeBefore = executor.getActiveCount();
        McTalking.LOGGER.info(
                "[DeepSeekQueue] Submitting task. Active={}/{}, QueuedBefore={}",
                activeBefore, MAX_CONCURRENT, queuedBefore
        );
        return executor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                task.run();
            } catch (Exception e) {
                McTalking.LOGGER.error("[DeepSeekQueue] Task threw exception", e);
            } finally {
                long duration = System.currentTimeMillis() - start;
                McTalking.LOGGER.info(
                        "[DeepSeekQueue] Task finished in {}ms. Active={}/{}, Queued={}",
                        duration, executor.getActiveCount(), MAX_CONCURRENT, executor.getQueue().size()
                );
            }
        });
    }

    /**
     * Number of tasks currently executing.
     */
    public static int getActiveCount() {
        return executor.getActiveCount();
    }

    /**
     * Number of tasks waiting in the FCFS queue.
     */
    public static int getQueueSize() {
        return executor.getQueue().size();
    }
}
