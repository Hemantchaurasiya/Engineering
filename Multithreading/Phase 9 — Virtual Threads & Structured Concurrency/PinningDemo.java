package com.orderengine.phase9;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Proves the single most important virtual-thread pitfall, concretely:
 * on this project's Java 21 baseline, a virtual thread that blocks
 * WHILE HOLDING A `synchronized` MONITOR does not unmount from its
 * carrier platform thread — it stays "pinned," occupying that carrier
 * for the ENTIRE blocking duration, exactly like a platform thread would.
 * With a small number of carriers (by default ~= available processors),
 * enough pinned virtual threads can silently bottleneck EXACTLY as a
 * small platform-thread pool would — reintroducing the wave-based
 * queuing NotificationBenchmark showed virtual threads eliminating,
 * except now hidden behind code that looks like it should be using
 * "unlimited cheap virtual threads."
 *
 * This is a genuinely dangerous pitfall because the code compiles,
 * passes ordinary testing, and doesn't throw any error — it just quietly
 * doesn't scale as far as everyone assumed it would, the same silent-
 * degradation shape as Phase 7 §5's common-pool starvation and Phase 8's
 * blocking-inside-parallel-stream danger. This project's Java 21 is the
 * version where this limitation is real; it's worth knowing the JDK team
 * has continued improving this over time in later releases — always
 * check current JDK release notes for the version actually in use in
 * production rather than assuming this project's snapshot is permanent.
 *
 * THE FIX: java.util.concurrent locks (ReentrantLock, and everything
 * built on the Lock interface — the whole Phase 2 toolkit) do NOT pin.
 * A virtual thread blocked waiting to acquire a ReentrantLock, or
 * blocked on I/O while HOLDING one, unmounts from its carrier normally.
 * This is a genuine, concrete reason (beyond Phase 2's original
 * throughput-under-read-contention motivation) to prefer
 * java.util.concurrent locks over `synchronized` for any code path that
 * might run on a virtual thread and perform blocking operations while
 * holding the lock.
 *
 * HOW THIS DEMO MEASURES PINNING WITHOUT SPECIAL TOOLING: each task
 * increments a shared "currently executing" counter immediately before
 * its blocking operation and decrements it immediately after, tracking
 * the observed PEAK concurrent count. Each task locks on its OWN,
 * per-task lock object (not shared across tasks) specifically so there
 * is no real mutual-exclusion contention between different tasks — the
 * only thing that can limit how many tasks are concurrently mid-block at
 * once is carrier availability. If pinning is happening, the peak
 * concurrent count will plateau near the carrier pool size; if it's not,
 * it should reach close to the full task count.
 *
 * RUN THIS WITH A DELIBERATELY SMALL CARRIER POOL to make the effect
 * unambiguous — see the run command in the phase notes:
 *   -Djdk.virtualThreadScheduler.parallelism=2
 */
public class PinningDemo {

    private static final int TASK_COUNT = 50;
    private static final long BLOCK_MILLIS = 150;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Blocking INSIDE synchronized: PINS the carrier thread ===");
        int pinnedPeak = runScenario(true);
        System.out.println("  peak concurrently-blocked tasks observed: " + pinnedPeak + " (of " + TASK_COUNT + " total)");

        System.out.println("\n=== Blocking INSIDE ReentrantLock: does NOT pin ===");
        int unpinnedPeak = runScenario(false);
        System.out.println("  peak concurrently-blocked tasks observed: " + unpinnedPeak + " (of " + TASK_COUNT + " total)");

        System.out.println("\nRESULT: " + (unpinnedPeak > pinnedPeak
                ? "as expected — ReentrantLock allowed meaningfully more concurrent in-flight tasks "
                        + "than synchronized did, because synchronized pinned each blocked virtual thread "
                        + "to a scarce carrier for the full blocking duration"
                : "run again with -Djdk.virtualThreadScheduler.parallelism=2 to make the carrier "
                        + "bottleneck small enough for the difference to show clearly"));
    }

    private static int runScenario(boolean useSynchronized) throws InterruptedException {
        AtomicInteger currentlyBlocked = new AtomicInteger(0);
        AtomicInteger peakBlocked = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASK_COUNT; i++) {
                if (useSynchronized) {
                    Object perTaskLock = new Object(); // distinct per task — no real contention, isolates pinning
                    executor.submit(() -> {
                        synchronized (perTaskLock) {
                            trackAndBlock(currentlyBlocked, peakBlocked);
                        }
                    });
                } else {
                    ReentrantLock perTaskLock = new ReentrantLock(); // also distinct per task
                    executor.submit(() -> {
                        perTaskLock.lock();
                        try {
                            trackAndBlock(currentlyBlocked, peakBlocked);
                        } finally {
                            perTaskLock.unlock();
                        }
                    });
                }
            }
        } // try-with-resources: waits for all submitted virtual threads to finish before continuing

        return peakBlocked.get();
    }

    private static void trackAndBlock(AtomicInteger currentlyBlocked, AtomicInteger peakBlocked) {
        int now = currentlyBlocked.incrementAndGet();
        peakBlocked.updateAndGet(prev -> Math.max(prev, now));
        try {
            Thread.sleep(BLOCK_MILLIS); // the blocking operation whose pinning behavior we're measuring
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            currentlyBlocked.decrementAndGet();
        }
    }
}
