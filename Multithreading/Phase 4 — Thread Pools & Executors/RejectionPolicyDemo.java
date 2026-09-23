package com.orderengine.phase4;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A rejection only happens once corePoolSize, the queue, AND maxPoolSize
 * are all simultaneously saturated (see ThreadPoolExecutorInternalsDemo
 * for the full sequence). What happens to that rejected task is
 * genuinely different production behavior depending on the handler, not
 * a cosmetic choice — this demo saturates an identically-sized pool
 * (core=max=2, queue capacity=2) with the same 10-task burst under each
 * of the four built-in policies plus a custom one, so the difference is
 * directly observable.
 *
 *  - AbortPolicy (the JDK default if none is specified): throws
 *    RejectedExecutionException back to the CALLER. Used by StageExecutors
 *    for the payment pool — a rejected payment task must be a loud,
 *    immediate, synchronous failure the caller can act on, never silent.
 *
 *  - CallerRunsPolicy: the calling thread runs the task itself,
 *    synchronously, instead of handing it to the pool. This is a
 *    genuine backpressure mechanism, not just "don't lose the task" —
 *    it slows the PRODUCER down (since it's now busy running the task
 *    instead of submitting more), which naturally throttles the rate of
 *    new submissions to match actual processing capacity. Used by
 *    StageExecutors for validation/reservation.
 *
 *  - DiscardPolicy: silently drops the rejected task, no exception, no
 *    log, nothing. Rarely correct on its own — usually only acceptable
 *    for genuinely disposable work where the caller has no way to react
 *    anyway and dropping is truly free of consequence.
 *
 *  - DiscardOldestPolicy: drops the OLDEST task currently sitting in the
 *    queue, then retries submitting the new one. Useful for the
 *    "freshness beats completeness" case — used by StageExecutors for
 *    notifications, where a newer notification is more valuable than an
 *    old queued one.
 *
 *  - Custom handler: the interface is a single method, so writing your
 *    own (e.g., log the rejection with full context, publish a metric,
 *    and only THEN decide whether to run/drop/retry) is common in real
 *    systems where none of the four built-ins captures the exact
 *    observability requirement.
 */
public class RejectionPolicyDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== AbortPolicy ===");
        runWith(new ThreadPoolExecutor.AbortPolicy());

        System.out.println("\n=== CallerRunsPolicy ===");
        runWith(new ThreadPoolExecutor.CallerRunsPolicy());

        System.out.println("\n=== DiscardPolicy ===");
        runWith(new ThreadPoolExecutor.DiscardPolicy());

        System.out.println("\n=== DiscardOldestPolicy ===");
        runWith(new ThreadPoolExecutor.DiscardOldestPolicy());

        System.out.println("\n=== Custom handler (log + metric + drop) ===");
        AtomicInteger rejectedCount = new AtomicInteger(0);
        RejectedExecutionHandler custom = (task, exec) -> {
            rejectedCount.incrementAndGet();
            System.out.println("  [custom-handler] rejected a task; pool queue size="
                    + exec.getQueue().size() + " activeCount=" + exec.getActiveCount());
        };
        runWith(custom);
        System.out.println("  total rejections observed by custom handler: " + rejectedCount.get());
    }

    private static void runWith(RejectedExecutionHandler handler) throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2),
                new NamedThreadFactory("rejection-demo", false),
                handler);

        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger callerRan = new AtomicInteger(0);

        for (int i = 1; i <= 10; i++) {
            final int taskId = i;
            Runnable task = () -> {
                if (Thread.currentThread().getName().startsWith("main")) {
                    callerRan.incrementAndGet();
                }
                sleep(200);
                completed.incrementAndGet();
            };
            try {
                executor.execute(task);
            } catch (RejectedExecutionException e) {
                System.out.println("  task-" + taskId + " rejected: caller received RejectedExecutionException");
            }
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  completed=" + completed.get()
                + (callerRan.get() > 0 ? " (of which " + callerRan.get() + " ran on the calling thread)" : ""));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
