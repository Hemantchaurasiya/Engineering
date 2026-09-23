package com.orderengine.phase11;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Starvation is a THIRD distinct failure mode, different from both
 * deadlock and livelock: the starved thread is not blocked forever
 * (deadlock) and the system as a WHOLE is not stuck making zero progress
 * (livelock) — overall throughput can look completely healthy — but ONE
 * SPECIFIC thread never gets its fair share of a contended resource,
 * for as long as other threads keep "barging" ahead of it.
 *
 * THE SCENARIO: five "greedy" threads and one "victim" thread all
 * repeatedly contend for the same non-fair ReentrantLock, hammering it
 * as fast as possible. Non-fair locking (the default, and normally the
 * right choice for throughput — see Phase 2 §4) allows ANY thread
 * requesting the lock to "barge" ahead of threads that have already been
 * waiting, if the lock happens to be free at that exact instant a new
 * request arrives. Under sustained, aggressive contention from many
 * threads that keep re-requesting immediately after releasing, a
 * specific unlucky thread can be repeatedly barged past — not because
 * anything is technically broken, but because the scheduling/barging
 * policy has no notion of "this one has been waiting longest, its turn
 * next."
 *
 * FAIR locking (`new ReentrantLock(true)`) fixes this directly: waiting
 * threads are granted the lock in FIFO arrival order, so the victim
 * thread's wait time is bounded by the number of threads ahead of it in
 * queue, not by how unlucky its timing continues to be — at a real
 * throughput cost, since barging (letting whichever thread happens to be
 * ready RIGHT NOW go first) is faster in aggregate than strictly
 * honoring arrival order.
 */
public class StarvationDemo {

    private static final int GREEDY_THREAD_COUNT = 5;
    private static final long RUN_DURATION_MILLIS = 1500;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Non-fair lock: victim thread can be starved under sustained contention ===");
        runScenario(false);

        System.out.println("\n=== Fair lock: victim thread gets its proportional share ===");
        runScenario(true);
    }

    private static void runScenario(boolean fair) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock(fair);
        AtomicInteger[] acquisitionCounts = new AtomicInteger[GREEDY_THREAD_COUNT + 1]; // last index = victim
        for (int i = 0; i < acquisitionCounts.length; i++) acquisitionCounts[i] = new AtomicInteger(0);

        StopFlag running = new StopFlag();
        running.value = true;

        Thread[] threads = new Thread[GREEDY_THREAD_COUNT + 1];
        for (int i = 0; i < GREEDY_THREAD_COUNT; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                while (running.value) {
                    lock.lock();
                    try {
                        acquisitionCounts[idx].incrementAndGet();
                        // Hold briefly then immediately re-request — the
                        // aggressive, unbroken re-contention pattern that
                        // makes barging so effective under non-fair locking.
                    } finally {
                        lock.unlock();
                    }
                }
            }, "greedy-" + i);
        }
        int victimIdx = GREEDY_THREAD_COUNT;
        threads[victimIdx] = new Thread(() -> {
            while (running.value) {
                lock.lock();
                try {
                    acquisitionCounts[victimIdx].incrementAndGet();
                } finally {
                    lock.unlock();
                }
            }
        }, "victim");

        for (Thread t : threads) t.start();
        Thread.sleep(RUN_DURATION_MILLIS);
        running.value = false;
        for (Thread t : threads) t.join(2000);

        int victimCount = acquisitionCounts[victimIdx].get();
        int totalGreedy = 0;
        for (int i = 0; i < GREEDY_THREAD_COUNT; i++) totalGreedy += acquisitionCounts[i].get();
        int totalAll = totalGreedy + victimCount;
        double victimShare = totalAll == 0 ? 0 : (100.0 * victimCount / totalAll);
        double fairShareExpected = 100.0 / (GREEDY_THREAD_COUNT + 1);

        System.out.printf("  victim acquisitions: %,d out of %,d total (%.2f%% of the lock; a fair 1/%d "
                        + "share would be ~%.1f%%)%n",
                victimCount, totalAll, victimShare, GREEDY_THREAD_COUNT + 1, fairShareExpected);
        System.out.println("  " + (fair
                ? "fair locking: victim's share should be close to its proportional 1/" + (GREEDY_THREAD_COUNT + 1) + " share"
                : "non-fair locking: victim's share can be dramatically below its proportional share — "
                        + "this is starvation, and note the overall system (total acquisitions across all "
                        + "6 threads) was NOT stuck — throughput looked fine, only this one thread suffered"));
    }

    /** Plain mutable holder — simplest way to share a stop flag across the lambdas above without extra ceremony. */
    private static final class StopFlag {
        volatile boolean value;
    }
}
