package com.orderengine.phase4;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Makes the ThreadPoolExecutor task-submission algorithm visible, since
 * it's frequently misunderstood — the most common wrong mental model is
 * "it fills up to maxPoolSize before it starts queuing", which is
 * BACKWARDS from what actually happens.
 *
 * The real algorithm, in order, on every execute(task) call:
 *
 *   1. If fewer than corePoolSize threads exist, start a new thread to
 *      run this task — even if other core threads are currently idle.
 *      (Core threads are created lazily as work arrives, not eagerly at
 *      construction — unless prestartAllCoreThreads() is called.)
 *
 *   2. Otherwise (core pool is full), try to enqueue the task in the
 *      work queue WITHOUT creating a new thread.
 *
 *   3. If the queue rejects the task (it's bounded and full), THEN try
 *      to create a new thread up to maxPoolSize.
 *
 *   4. If the pool is already at maxPoolSize too, the task is handed to
 *      the RejectedExecutionHandler.
 *
 * The counterintuitive consequence: with a bounded queue that has real
 * capacity, the pool will NEVER grow past corePoolSize until the queue
 * is completely full — extra threads beyond core size are a last resort,
 * not a first response to load. This demo uses a deliberately tiny pool
 * (core=2, max=4) and a deliberately tiny queue (capacity=2) with slow
 * tasks, so you can watch getPoolSize() step through exactly this
 * sequence as tasks are submitted one at a time.
 */
public class ThreadPoolExecutorInternalsDemo {

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,                                  // corePoolSize
                4,                                  // maximumPoolSize
                5, TimeUnit.SECONDS,                 // keepAliveTime for threads beyond core
                new ArrayBlockingQueue<>(2),          // bounded work queue, capacity 2
                new NamedThreadFactory("demo", false));

        System.out.println("core=2 max=4 queueCapacity=2 — submitting 8 slow tasks one at a time\n");

        for (int i = 1; i <= 8; i++) {
            final int taskId = i;
            try {
                executor.execute(() -> {
                    System.out.println("  [task-" + taskId + "] running on "
                            + Thread.currentThread().getName());
                    sleep(1500);
                });
                report(executor, "submitted task-" + taskId);
            } catch (RejectedExecutionException e) {
                System.out.println("  task-" + taskId + " REJECTED: " + e.getMessage());
            }
            Thread.sleep(150); // small stagger so state prints are readable in order
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println("\ndone.");
    }

    private static void report(ThreadPoolExecutor executor, String label) {
        System.out.printf("  after %-16s poolSize=%d activeCount=%d queueSize=%d largestPoolSize=%d%n",
                label, executor.getPoolSize(), executor.getActiveCount(),
                executor.getQueue().size(), executor.getLargestPoolSize());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
