package com.orderengine.phase4;

/**
 * Implements the two standard thread-pool sizing formulas (from Brian
 * Goetz's "Java Concurrency in Practice") that every StageExecutors pool
 * size in this phase is derived from, instead of picking round numbers.
 *
 * CPU-BOUND WORK (validation logic, business-rule checks, JSON parsing —
 * work dominated by actual computation, not waiting):
 *
 *   Nthreads = Ncpu + 1
 *
 * The "+1" is standard practical guidance so that when a thread
 * occasionally takes a page fault or is briefly descheduled, another
 * thread can keep a core busy — going meaningfully above Ncpu wastes
 * time on context switching between threads all fighting for the same
 * physical cores with no I/O wait to overlap.
 *
 * I/O-BOUND WORK (payment gateway HTTP calls, notification delivery,
 * database round-trips — work dominated by WAITING on something
 * external, not computing):
 *
 *   Nthreads = Ncpu * Utarget * (1 + W/C)
 *
 * where:
 *   Ncpu    = number of CPU cores available
 *   Utarget = target CPU utilization, 0.0-1.0 (usually aim for ~0.8-1.0
 *             if this pool should be allowed to use the CPU fully;
 *             lower it if you want to reserve headroom for other pools
 *             sharing the same machine)
 *   W       = average wait time per task (time spent blocked on I/O)
 *   C       = average compute time per task (time spent actually using
 *             the CPU)
 *
 * Intuition: if a task spends 9x as long waiting on the network as it
 * spends computing (W/C = 9), a single core can usefully support ~10
 * concurrent such tasks before the CPU itself becomes the bottleneck,
 * because 9 of every 10 "in flight" tasks are just waiting, not
 * competing for the core at all. This is precisely why the payment and
 * notification pools in StageExecutors are sized far larger than the
 * validation/reservation pools despite running on the same hardware —
 * the work profile, not a guess, drives the number.
 */
public final class PoolSizingCalculator {

    private PoolSizingCalculator() {}

    public static int cpuBoundPoolSize() {
        return Runtime.getRuntime().availableProcessors() + 1;
    }

    /**
     * @param targetUtilization  0.0-1.0, how much of the CPU this pool
     *                           should be allowed to consume
     * @param waitTimeMillis     average time a task spends blocked on I/O
     * @param computeTimeMillis  average time a task spends actually
     *                           using the CPU
     */
    public static int ioBoundPoolSize(double targetUtilization,
                                       double waitTimeMillis,
                                       double computeTimeMillis) {
        if (computeTimeMillis <= 0) {
            throw new IllegalArgumentException("computeTimeMillis must be > 0 to compute a wait/compute ratio");
        }
        int ncpu = Runtime.getRuntime().availableProcessors();
        double waitToComputeRatio = waitTimeMillis / computeTimeMillis;
        double raw = ncpu * targetUtilization * (1 + waitToComputeRatio);
        return Math.max(1, (int) Math.ceil(raw));
    }
}
