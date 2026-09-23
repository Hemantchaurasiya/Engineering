package com.orderengine.phase7;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncPitfallsDemo {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        System.out.println("=== Pitfall 1: thenApply vs thenApplyAsync — which thread runs the continuation? ===");
        demoThenApplyThreadIdentity();

        System.out.println("\n=== Pitfall 2: an exception nobody ever observes vanishes silently ===");
        demoSwallowedException();

        System.out.println("\n=== Pitfall 3: blocking work on the shared ForkJoinPool.commonPool() starves unrelated code ===");
        demoCommonPoolStarvation();
    }

    private static void demoThenApplyThreadIdentity() throws ExecutionException, InterruptedException {
        // thenApply (no "Async" suffix): if the future is ALREADY
        // complete when thenApply is called, the continuation runs on
        // the CALLING thread, right there, synchronously. If the future
        // is NOT yet complete, the continuation runs on whichever thread
        // ends up COMPLETING the future (e.g. the executor thread that
        // finished the supplyAsync work) — NOT necessarily the thread
        // that called thenApply. This dual behavior is a common source
        // of confusion: which thread actually runs your code is not
        // fixed, it depends on timing.
        CompletableFuture<String> alreadyDone = CompletableFuture.completedFuture("x");
        String threadForAlreadyDone = alreadyDone.thenApply(v -> Thread.currentThread().getName()).get();
        System.out.println("  thenApply on an ALREADY-COMPLETE future ran on: " + threadForAlreadyDone
                + "  (== calling thread, since there was nothing to wait for)");

        CompletableFuture<String> notYetDone = CompletableFuture.supplyAsync(() -> {
            sleep(200);
            return "y";
        }); // default executor: ForkJoinPool.commonPool() — fine for pure CPU work, NOT for I/O; see pitfall 3
        String threadForNotYetDone = notYetDone.thenApply(v -> Thread.currentThread().getName()).get();
        System.out.println("  thenApply on a NOT-YET-COMPLETE future ran on: " + threadForNotYetDone
                + "  (the pool thread that completed supplyAsync, not the calling thread)");

        // thenApplyAsync WITH an explicit executor: deterministic,
        // regardless of timing — always runs on that executor, never on
        // the calling thread and never on whatever thread happened to
        // complete the future. This determinism is exactly why
        // PaymentOrchestrator's own gateway clients always pass an
        // explicit executor to supplyAsync rather than relying on
        // whichever thread happens to complete things.
        var dedicatedExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "dedicated-continuation-thread"));
        String threadForAsyncWithExecutor = CompletableFuture.completedFuture("z")
                .thenApplyAsync(v -> Thread.currentThread().getName(), dedicatedExecutor)
                .get();
        System.out.println("  thenApplyAsync(fn, explicitExecutor) ran on: " + threadForAsyncWithExecutor
                + "  (deterministic — always this executor, regardless of completion timing)");
        dedicatedExecutor.shutdown();
    }

    private static void demoSwallowedException() throws InterruptedException {
        // A CompletableFuture chain that throws, with NOTHING ever
        // calling get()/join()/whenComplete() on the terminal future,
        // loses the exception completely — no log, no crash, nothing.
        // This is the same shape as Phase 4 §6's submit()-without-get()
        // pitfall, one level more composable and therefore easier to
        // lose track of across a longer chain.
        CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("boom — thrown inside an async chain nobody observes");
        }).thenApply(v -> "never reached");
        // ^ result intentionally discarded; nothing calls get()/join() on it

        Thread.sleep(300); // give the async task time to actually run and throw
        System.out.println("  (the exception above was thrown on a background thread and is now gone forever — "
                + "no output from it appears anywhere, because nothing ever called get()/join()/whenComplete() "
                + "on the resulting future)");

        // Contrast: whenComplete/handle/exceptionally DOES observe it,
        // even without a caller ever blocking on get().
        CountDownLatch observed = new CountDownLatch(1);
        CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("boom — this one IS observed");
        }).whenComplete((result, error) -> {
            if (error != null) {
                System.out.println("  correctly observed via whenComplete(): " + error.getCause().getMessage());
            }
            observed.countDown();
        });
        observed.await(2, TimeUnit.SECONDS);
    }

    private static void demoCommonPoolStarvation() throws InterruptedException {
        int commonPoolParallelism = java.util.concurrent.ForkJoinPool.commonPool().getParallelism();
        System.out.println("  ForkJoinPool.commonPool() parallelism on this machine: " + commonPoolParallelism);

        AtomicLong parallelStreamCompletions = new AtomicLong(0);
        CountDownLatch blockersStarted = new CountDownLatch(commonPoolParallelism);
        CountDownLatch releaseBlockers = new CountDownLatch(1);

        // Saturate the ENTIRE common pool with blocking (not CPU-bound!)
        // work — exactly the mistake of submitting a blocking I/O call to
        // supplyAsync() without an explicit executor, at a scale that
        // consumes every common-pool thread simultaneously.
        for (int i = 0; i < commonPoolParallelism; i++) {
            CompletableFuture.runAsync(() -> {
                blockersStarted.countDown();
                await(releaseBlockers); // simulates a blocking network call — never returns until released
            });
        }
        blockersStarted.await(2, TimeUnit.SECONDS);
        System.out.println("  every common-pool thread is now blocked simulating an I/O call...");

        // Now try to run something completely unrelated that ALSO wants
        // the common pool — a parallel stream, used elsewhere in the same
        // JVM for entirely unrelated work.
        Thread parallelStreamAttempt = new Thread(() -> {
            java.util.stream.IntStream.range(0, 4).parallel().forEach(i -> {
                parallelStreamCompletions.incrementAndGet();
            });
        });
        parallelStreamAttempt.start();
        parallelStreamAttempt.join(500);

        System.out.println("  unrelated parallel stream finished within 500ms while pool was saturated: "
                + !parallelStreamAttempt.isAlive()
                + "  (a parallel stream degrades to running on the CALLING thread when the common "
                + "pool has no free workers, rather than deadlocking — but this masks the fact that "
                + "NONE of its work actually got the parallelism it was written to expect, silently, "
                + "with no error)");

        releaseBlockers.countDown();
        parallelStreamAttempt.join();
        System.out.println("  released the blocked common-pool threads.");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
