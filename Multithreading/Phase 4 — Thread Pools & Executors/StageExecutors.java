package com.orderengine.phase4;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Builds one tuned ThreadPoolExecutor per pipeline stage. Every stage
 * gets a DIFFERENT sizing strategy and rejection policy, because every
 * stage has a genuinely different work profile — this is the payoff of
 * Phase 4: we stop using one generic pool for everything (a very common
 * real-world anti-pattern) and size each pool for what it actually does.
 *
 * validation        — CPU-bound: parsing/business-rule checks on the raw
 *                      Order. Small pool (Ncpu+1), backpressure via
 *                      CallerRunsPolicy: if the queue fills, the calling
 *                      (ingestion) thread does the validation itself,
 *                      which naturally throttles intake instead of
 *                      unboundedly queuing.
 *
 * inventoryReservation — Also effectively CPU-bound once you account for
 *                      Phase 2's InventoryLedger being an in-memory,
 *                      lock/CAS-protected structure with no real I/O.
 *                      Same sizing strategy as validation, same
 *                      CallerRunsPolicy backpressure rationale.
 *
 * payment           — I/O-bound: blocking network calls to an external
 *                      payment gateway. Sized far larger relative to
 *                      core count using the wait/compute formula, because
 *                      most "in-flight" payment tasks are just waiting on
 *                      the network, not consuming a core. Uses AbortPolicy
 *                      deliberately — a rejected payment task must
 *                      surface as a loud, immediate failure the caller
 *                      can react to (e.g. retry via Phase 12's resilience
 *                      patterns), never be silently dropped or throttled
 *                      into an unbounded backlog of money-moving work.
 *
 * notification      — I/O-bound, but best-effort: an order confirmation
 *                      email/SMS that fails or is dropped under overload
 *                      is not a correctness problem the way a lost
 *                      payment would be. Uses DiscardOldestPolicy: under
 *                      sustained overload, prefer sending the newest
 *                      notifications over the oldest (a customer doesn't
 *                      need a 10-minute-stale "order received" email once
 *                      3 more orders have since shipped).
 */
public final class StageExecutors {

    private final ThreadPoolExecutor validationExecutor;
    private final ThreadPoolExecutor inventoryReservationExecutor;
    private final ThreadPoolExecutor paymentExecutor;
    private final ThreadPoolExecutor notificationExecutor;

    public StageExecutors() {
        int cpuBoundSize = PoolSizingCalculator.cpuBoundPoolSize();

        // Payment: assume ~4ms actual CPU work building/parsing the
        // request/response vs ~120ms average network wait per call —
        // heavily I/O-bound, W/C = 30.
        int paymentPoolSize = PoolSizingCalculator.ioBoundPoolSize(0.9, 120, 4);

        // Notification: similar profile, slightly cheaper compute.
        int notificationPoolSize = PoolSizingCalculator.ioBoundPoolSize(0.9, 80, 2);

        this.validationExecutor = buildExecutor(
                "validation", cpuBoundSize, cpuBoundSize,
                queueOf(200), new ThreadPoolExecutor.CallerRunsPolicy());

        this.inventoryReservationExecutor = buildExecutor(
                "inventory-reservation", cpuBoundSize, cpuBoundSize,
                queueOf(200), new ThreadPoolExecutor.CallerRunsPolicy());

        this.paymentExecutor = buildExecutor(
                "payment", paymentPoolSize, paymentPoolSize,
                queueOf(500), new ThreadPoolExecutor.AbortPolicy());

        this.notificationExecutor = buildExecutor(
                "notification", notificationPoolSize, notificationPoolSize,
                queueOf(1000), new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    private ThreadPoolExecutor buildExecutor(String stageName,
                                              int coreSize,
                                              int maxSize,
                                              ArrayBlockingQueue<Runnable> queue,
                                              RejectedExecutionHandler rejectionHandler) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L, TimeUnit.SECONDS,        // keep-alive: irrelevant here since core==max,
                                              // see phase notes §3 on when it matters
                queue,
                new NamedThreadFactory(stageName, false),
                rejectionHandler);
        return executor;
    }

    private ArrayBlockingQueue<Runnable> queueOf(int capacity) {
        return new ArrayBlockingQueue<>(capacity);
    }

    public ThreadPoolExecutor validation() { return validationExecutor; }
    public ThreadPoolExecutor inventoryReservation() { return inventoryReservationExecutor; }
    public ThreadPoolExecutor payment() { return paymentExecutor; }
    public ThreadPoolExecutor notification() { return notificationExecutor; }

    /**
     * Graceful shutdown, in the correct order for a pipeline: stop
     * accepting new work at the front (validation) first, drain each
     * stage in turn, then move to the next stage — this avoids the
     * failure mode of shutting every stage down simultaneously while
     * work is still mid-flight between them. See phase notes §7 for why
     * shutdown() (not shutdownNow()) is the right default here, and what
     * shutdownNow() would actually do differently.
     */
    public void shutdownGracefully(long timeoutSecondsPerStage) throws InterruptedException {
        for (ThreadPoolExecutor executor : List.of(
                validationExecutor, inventoryReservationExecutor, paymentExecutor, notificationExecutor)) {
            executor.shutdown();
            boolean finished = executor.awaitTermination(timeoutSecondsPerStage, TimeUnit.SECONDS);
            if (!finished) {
                System.err.println("[shutdown] pool did not drain within timeout, forcing: "
                        + executor);
                List<Runnable> abandoned = executor.shutdownNow();
                System.err.println("[shutdown] abandoned " + abandoned.size() + " queued tasks");
            }
        }
    }
}
