package com.orderengine.phase12;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Named after a ship's bulkheads — physical compartment walls designed
 * so that if ONE compartment floods, the others stay dry and the ship
 * doesn't sink. Applied to software: give each downstream dependency its
 * OWN dedicated, isolated resource pool (here, a small dedicated
 * ExecutorService), so a dependency that starts timing out or hanging
 * can only ever exhaust ITS OWN pool — it can never consume threads that
 * a completely different, healthy dependency's calls also need.
 *
 * WHY THIS IS A DIFFERENT PROBLEM FROM RATE LIMITING OR CIRCUIT
 * BREAKING: neither of those alone prevents cross-dependency
 * contamination if multiple dependencies share one common thread pool.
 * Concretely: imagine payment-gateway calls and fraud-check calls (Phase
 * 7's PaymentOrchestrator) sharing ONE thread pool. If the payment
 * gateway starts hanging (not failing fast enough to trip a circuit
 * breaker quickly, just slow), its calls occupy pool threads for a long
 * time each. Given enough concurrent orders, EVERY thread in the shared
 * pool can end up tied up waiting on the slow payment gateway — leaving
 * ZERO threads available for fraud-check calls, even though the fraud
 * check service itself is perfectly healthy. The fraud check service
 * becomes collateral damage of an outage it has nothing to do with,
 * purely because of shared-resource contention. Separate, bulkheaded
 * pools prevent this structurally: the payment gateway's pool can be
 * 100% saturated and hung, and the fraud-check pool is completely
 * unaffected, continuing to serve its own calls normally.
 *
 * This is the SAME underlying idea Phase 4's StageExecutors (separate
 * pools per pipeline stage) and Phase 7/8's dedicated executors (never
 * sharing ForkJoinPool.commonPool() for real work) were already
 * practicing — this phase just gives the pattern its standard resilience-
 * engineering name and frames it explicitly as a FAILURE-ISOLATION
 * technique rather than just a performance-tuning one.
 */
public class Bulkhead {

    private final String name;
    private final ExecutorService pool;

    public Bulkhead(String name, int poolSize) {
        this.name = name;
        this.pool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "bulkhead-" + name + "-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Submits work to THIS bulkhead's isolated pool and waits up to
     * timeout for a result. If the pool is fully saturated by other
     * calls to the SAME dependency, this call queues or times out — but
     * critically, it can NEVER be starved by a DIFFERENT dependency's
     * bulkhead, because they don't share threads at all.
     */
    public <T> T call(Callable<T> operation, long timeout, TimeUnit unit) throws Exception {
        Future<T> future = pool.submit(operation);
        try {
            return future.get(timeout, unit);
        } catch (Exception e) {
            future.cancel(true); // don't leave an abandoned task quietly consuming a pool thread indefinitely
            throw e;
        }
    }

    public String name() {
        return name;
    }

    public void shutdown() {
        pool.shutdown();
    }
}
