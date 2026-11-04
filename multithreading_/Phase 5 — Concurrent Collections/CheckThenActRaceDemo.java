package com.orderengine.phase5;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proves, with real races, that OrderStatusTracker.advanceToReservedBroken()
 * is genuinely unsafe despite being built entirely out of thread-safe
 * ConcurrentHashMap calls — the map protects each individual get()/put(),
 * not the SEQUENCE of them.
 *
 * Two versions of the proof:
 *
 *  1. A NATURAL race: many threads simply race the same key with no
 *     forced interleaving, relying on real OS scheduling to occasionally
 *     produce the losing interleaving. This is realistic (it's what
 *     actually happens in production) but probabilistic — whether it
 *     reproduces on a given run depends on core count and scheduler
 *     behavior; on a single-core machine especially, natural context
 *     switches tend to be coarse enough that a tiny get()-then-put()
 *     window may rarely get split, so this version can pass clean
 *     even though the code is still genuinely unsafe.
 *
 *  2. A DETERMINISTIC forced race (via OrderStatusTracker's *WithHook
 *     test methods and CountDownLatch coordination, the same technique
 *     Phase 3's AbaProblemDemo uses): one thread is paused BY CODE at
 *     exactly the vulnerable point — after its get(), before its put() —
 *     while a second thread completes its own full get()-then-put()
 *     cycle, before the first thread is allowed to resume. This
 *     reproduces the bug on every single run, on any hardware, which is
 *     what you actually want from a demo whose job is to PROVE a bug
 *     exists rather than hope you get unlucky enough to see it.
 *
 * A third scenario proves the compute()-based fix by TIMING rather than
 * counting: because compute() holds the per-key bin lock for its entire
 * duration, a second thread's compute() call for the same key must fully
 * block until the first one (including an artificial delay inside it)
 * completes — real, measurable mutual exclusion, not just "it happened
 * to come out to the right count this run."
 */
public class CheckThenActRaceDemo {

    private static final long ORDER_ID = 42L;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 1. Natural race (many threads, no forced interleaving) ===");
        runNaturalRace();

        System.out.println("\n=== 2. Deterministic forced race: broken get()-then-put() ===");
        runDeterministicForcedRace();

        System.out.println("\n=== 3. Deterministic proof compute() actually serializes (by timing) ===");
        runComputeSerializationProof();
    }

    private static void runNaturalRace() throws InterruptedException {
        OrderStatusTracker tracker = new OrderStatusTracker();
        tracker.recordValidated(ORDER_ID);

        int threadCount = 64;
        AtomicInteger successCount = new AtomicInteger(0);
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                if (tracker.advanceToReservedBroken(ORDER_ID)) {
                    successCount.incrementAndGet();
                }
            });
        }
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        System.out.println("  " + threadCount + " threads raced naturally; successes=" + successCount.get());
        System.out.println("  RESULT: " + (successCount.get() == 1
                ? "no duplicate observed THIS run (the race is real but timing-dependent — "
                        + "see scenario 2 below for a guaranteed reproduction regardless of hardware)"
                : "BUG — " + successCount.get() + " threads believed they performed the single transition"));
    }

    private static void runDeterministicForcedRace() throws InterruptedException {
        OrderStatusTracker tracker = new OrderStatusTracker();
        tracker.recordValidated(ORDER_ID);

        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch threadAPaused = new CountDownLatch(1);
        CountDownLatch threadBDone = new CountDownLatch(1);

        Thread threadA = new Thread(() -> {
            boolean success = tracker.advanceToReservedBrokenWithHook(ORDER_ID, () -> {
                // Paused here BY CODE, deterministically, right after
                // A's get() returned VALIDATED and before A's put().
                threadAPaused.countDown();
                await(threadBDone);
            });
            if (success) successCount.incrementAndGet();
        }, "thread-A");

        Thread threadB = new Thread(() -> {
            await(threadAPaused); // wait until A is paused in exactly the vulnerable window
            boolean success = tracker.advanceToReservedBroken(ORDER_ID); // races A, completes fully
            if (success) successCount.incrementAndGet();
            threadBDone.countDown(); // let A resume
        }, "thread-B");

        threadA.start();
        threadB.start();
        threadA.join();
        threadB.join();

        System.out.println("  thread A paused after its get(), thread B completed a full get()+put() "
                + "cycle in that exact window, then A resumed and wrote anyway.");
        System.out.println("  successes=" + successCount.get());
        System.out.println("  RESULT: " + (successCount.get() == 2
                ? "BUG REPRODUCED (guaranteed, every run) — both threads believed they performed "
                        + "the single, supposed-to-be-exactly-once transition"
                : "unexpected — expected exactly 2"));
    }

    private static void runComputeSerializationProof() throws InterruptedException {
        OrderStatusTracker tracker = new OrderStatusTracker();
        tracker.recordValidated(ORDER_ID);

        CountDownLatch threadAInsideCompute = new CountDownLatch(1);
        long artificialDelayMillis = 500;

        Thread threadA = new Thread(() -> {
            tracker.advanceToReservedWithHook(ORDER_ID, () -> {
                threadAInsideCompute.countDown();
                sleep(artificialDelayMillis); // held INSIDE compute()'s critical section
            });
        }, "thread-A");

        threadA.start();
        await(threadAInsideCompute); // A is now inside its compute() call, sleeping

        long start = System.currentTimeMillis();
        tracker.advanceToReserved(ORDER_ID); // B's compute() call for the SAME key
        long elapsed = System.currentTimeMillis() - start;

        threadA.join();

        System.out.println("  thread A held the bin lock for ~" + artificialDelayMillis
                + "ms inside compute(). Thread B's own compute() call for the SAME key took "
                + elapsed + "ms to return.");
        System.out.println("  RESULT: " + (elapsed >= artificialDelayMillis - 50
                ? "confirmed — B was genuinely BLOCKED waiting for A's compute() to finish, "
                        + "real mutual exclusion, not just a coincidentally correct count"
                : "unexpected — B returned suspiciously fast, investigate"));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
