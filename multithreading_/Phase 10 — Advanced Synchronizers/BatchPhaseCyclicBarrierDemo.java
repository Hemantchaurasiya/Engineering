package com.orderengine.phase10;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CyclicBarrier vs CountDownLatch — the key difference the name says
 * directly: a CyclicBarrier RESETS and can be reused for another round,
 * where a CountDownLatch is one-shot and permanently open once it hits
 * zero (StartupCoordinator.java in this phase). Use CyclicBarrier
 * whenever a fixed group of threads needs to repeatedly reach a common
 * checkpoint together, over and over, before all proceeding to the next
 * phase together — exactly a multi-round batch job like this: N worker
 * threads each process their OWN chunk of round K's data, then ALL must
 * finish round K before ANY of them starts round K+1 (e.g. because round
 * K+1 depends on aggregated results from round K that only exist once
 * every worker's chunk is done).
 *
 * The barrier ACTION (the Runnable passed to the constructor) runs
 * exactly ONCE per round, on whichever thread happens to be the LAST one
 * to arrive at the barrier for that round — not on every thread, and not
 * on a separate dedicated thread. This is used here to print round
 * progress and reset per-round counters exactly once per round, without
 * needing any separate coordination for "who reports this round's
 * summary" — the barrier itself elects the reporter.
 */
public class BatchPhaseCyclicBarrierDemo {

    private static final int WORKER_COUNT = 4;
    private static final int ROUNDS = 5;
    private static final int ITEMS_PER_WORKER_PER_ROUND = 1000;

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger roundNumber = new AtomicInteger(0);
        AtomicLong totalProcessedThisRound = new AtomicLong(0);
        AtomicLong grandTotalProcessed = new AtomicLong(0);

        CyclicBarrier barrier = new CyclicBarrier(WORKER_COUNT, () -> {
            // Runs ONCE per round, on the last worker to arrive.
            int round = roundNumber.incrementAndGet();
            long thisRound = totalProcessedThisRound.getAndSet(0);
            grandTotalProcessed.addAndGet(thisRound);
            System.out.println("  === round " + round + " complete — "
                    + thisRound + " items processed this round, "
                    + grandTotalProcessed.get() + " total so far ===");
        });

        Thread[] workers = new Thread[WORKER_COUNT];
        for (int w = 0; w < WORKER_COUNT; w++) {
            final int workerId = w;
            workers[w] = new Thread(() -> {
                for (int round = 0; round < ROUNDS; round++) {
                    // Each worker does its own chunk of work for this round.
                    for (int i = 0; i < ITEMS_PER_WORKER_PER_ROUND; i++) {
                        // simulated per-item work
                    }
                    totalProcessedThisRound.addAndGet(ITEMS_PER_WORKER_PER_ROUND);
                    System.out.println("  worker-" + workerId + " finished round " + (round + 1)
                            + "'s chunk, waiting at barrier...");
                    try {
                        barrier.await(); // blocks until ALL WORKER_COUNT threads reach this point THIS round
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    // Every worker resumes together here, for the NEXT round —
                    // the barrier automatically reset itself for reuse.
                }
            }, "worker-" + w);
        }

        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        System.out.println("\nAll " + ROUNDS + " rounds complete. Grand total: " + grandTotalProcessed.get()
                + " (expected " + (long) WORKER_COUNT * ITEMS_PER_WORKER_PER_ROUND * ROUNDS + ")");
    }
}
