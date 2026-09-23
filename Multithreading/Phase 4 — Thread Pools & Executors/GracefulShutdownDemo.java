package com.orderengine.phase4;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Two related pitfalls that show up constantly in production incident
 * reviews, demonstrated concretely:
 *
 * 1. shutdown() vs shutdownNow() — NOT interchangeable.
 *      shutdown()    : stop accepting NEW tasks; let already-submitted
 *                      tasks (running AND queued) run to completion.
 *                      This is what StageExecutors.shutdownGracefully()
 *                      uses, because dropping an order mid-pipeline on
 *                      deploy is a real business problem.
 *      shutdownNow() : attempt to stop everything immediately —
 *                      interrupts all actively running threads (an
 *                      INTERRUPT, not a guarantee — a thread ignoring
 *                      InterruptedException / not checking
 *                      Thread.interrupted() will simply keep running)
 *                      and returns the list of tasks that were still
 *                      queued and never even started, as a
 *                      List<Runnable> the caller can inspect/requeue.
 *
 *    awaitTermination(timeout, unit) blocks until either all tasks
 *    finish after a shutdown()/shutdownNow() call, or the timeout
 *    elapses — it does NOT itself trigger shutdown, and calling it
 *    without first calling shutdown()/shutdownNow() will simply block
 *    for the full timeout since the pool never stops accepting work.
 *
 * 2. execute() vs submit() — different exception-handling contracts.
 *      execute(Runnable)     : an uncaught exception propagates to the
 *                              thread's UncaughtExceptionHandler (see
 *                              NamedThreadFactory) — silent unless you
 *                              wired one up.
 *      submit(Callable/Runnable) : returns a Future. An exception thrown
 *                              inside the task is CAUGHT by the
 *                              executor and stored inside the Future —
 *                              it is only re-thrown (wrapped in
 *                              ExecutionException) when you call
 *                              future.get(). If nobody ever calls
 *                              get() on that Future, the exception is
 *                              silently swallowed FOREVER — arguably a
 *                              worse footgun than execute()'s, because
 *                              it looks like it should be "safer" (a
 *                              Future feels like it's handling errors
 *                              for you) when actually it just relocates
 *                              where you're responsible for checking.
 */
public class GracefulShutdownDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== shutdown() lets queued/running work finish ===");
        demoGracefulShutdown();

        System.out.println("\n=== shutdownNow() interrupts running work, returns unstarted tasks ===");
        demoForcefulShutdown();

        System.out.println("\n=== execute() vs submit(): where exceptions go to die ===");
        demoExecuteVsSubmitExceptions();
    }

    private static void demoGracefulShutdown() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10),
                new NamedThreadFactory("graceful", false));

        for (int i = 1; i <= 5; i++) {
            final int id = i;
            executor.execute(() -> {
                System.out.println("  task-" + id + " running");
                sleep(300);
                System.out.println("  task-" + id + " completed");
            });
        }

        executor.shutdown(); // no new tasks accepted from here on
        boolean finishedInTime = executor.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  all 5 tasks completed before pool terminated: " + finishedInTime);
    }

    private static void demoForcefulShutdown() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10),
                new NamedThreadFactory("forceful", false));

        for (int i = 1; i <= 6; i++) {
            final int id = i;
            executor.execute(() -> {
                System.out.println("  task-" + id + " running");
                // A WELL-BEHAVED task checks its interrupted status and
                // stops early instead of swallowing the interrupt and
                // continuing regardless — this is what makes
                // shutdownNow()'s interrupt actually effective. A task
                // that catches InterruptedException and just carries on
                // (a common real mistake) would print "completed" anyway,
                // proving the point from the class javadoc that an
                // interrupt is a REQUEST, not a guarantee.
                if (sleepInterruptibly(2000)) {
                    System.out.println("  task-" + id + " completed (should NOT print for most tasks)");
                } else {
                    System.out.println("  task-" + id + " observed the interrupt and stopped early");
                }
            });
        }

        Thread.sleep(200); // let the first 2 (core size) actually start
        List<Runnable> neverStarted = executor.shutdownNow();
        System.out.println("  tasks that never even started, returned for possible requeue: "
                + neverStarted.size());
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void demoExecuteVsSubmitExceptions() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10),
                new NamedThreadFactory("exceptions", false));

        System.out.println("  execute() with a throwing task (watch for [UNCAUGHT] from the ThreadFactory):");
        executor.execute(() -> {
            throw new RuntimeException("boom from execute()");
        });
        Thread.sleep(200);

        System.out.println("  submit() with a throwing task, future.get() NEVER called: silently lost.");
        executor.submit(() -> {
            throw new RuntimeException("boom from submit(), nobody will ever see this");
        });
        Thread.sleep(200);

        System.out.println("  submit() with a throwing task, future.get() called: exception surfaces properly.");
        Future<?> future = executor.submit(() -> {
            throw new RuntimeException("boom from submit(), properly observed");
        });
        try {
            future.get();
        } catch (ExecutionException e) {
            System.out.println("  caught via future.get(): " + e.getCause());
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Returns true if the sleep completed normally, false if interrupted partway through. */
    private static boolean sleepInterruptibly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
