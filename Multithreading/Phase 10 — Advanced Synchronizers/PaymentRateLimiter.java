package com.orderengine.phase10;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guards the payment gateway call (Phase 7's PaymentGatewayClient in the
 * real engine) with a hard cap on CONCURRENT in-flight calls — a real
 * production need distinct from thread-pool sizing: a payment gateway's
 * own backend often enforces a max-concurrent-connections limit per
 * merchant account, independent of how many threads (platform OR
 * virtual — see Phase 9) the calling service happens to have available
 * to make calls. Phase 4's pool sizing controls how much PARALLEL WORK
 * capacity this service has; a Semaphore here controls how much of that
 * capacity is allowed to hit ONE specific downstream dependency at once
 * — a different, complementary concern, and doubly important once
 * Phase 9's virtual threads make it trivial to have thousands of
 * concurrent in-flight calls with no thread-count-based limit at all
 * naturally slowing things down.
 *
 * A counting Semaphore holds N "permits". acquire() blocks until a
 * permit is available (decrementing the count); release() returns one
 * (incrementing it). Unlike a lock, a Semaphore has NO notion of
 * "ownership" — the thread that calls release() does not have to be the
 * same thread that called acquire(), which is unusual compared to every
 * other synchronizer in this project so far and occasionally useful (a
 * producer acquiring a permit that a completely different consumer
 * thread later releases) — though this rate limiter uses the more
 * common same-thread acquire/release-in-finally pattern.
 */
public class PaymentRateLimiter {

    private final Semaphore permits;
    private final AtomicInteger rejectedCount = new AtomicInteger(0);
    private final AtomicInteger peakConcurrent = new AtomicInteger(0);
    private final AtomicInteger currentConcurrent = new AtomicInteger(0);

    public PaymentRateLimiter(int maxConcurrentCalls) {
        // fair=true: FIFO permit acquisition — under sustained load, this
        // avoids a scenario where a newly-arriving call can repeatedly
        // "barge" ahead of calls that have been waiting longer, exactly
        // Phase 2 §4's ReentrantReadWriteLock fairness trade-off, applied
        // here because starving a specific waiting caller indefinitely
        // under sustained load is a worse outcome than the modest
        // throughput cost fairness adds.
        this.permits = new Semaphore(maxConcurrentCalls, true);
    }

    /**
     * Blocks until a permit is available, then runs the call. Always
     * releases the permit, even on exception — the finally block here is
     * exactly as mandatory as it was for every explicit-lock usage since
     * Phase 2; a missed release() permanently shrinks the effective
     * concurrency limit by one, a slow, easy-to-miss resource leak.
     */
    public <T> T callWithLimit(java.util.concurrent.Callable<T> gatewayCall) throws Exception {
        permits.acquire();
        try {
            int now = currentConcurrent.incrementAndGet();
            peakConcurrent.updateAndGet(prev -> Math.max(prev, now));
            return gatewayCall.call();
        } finally {
            currentConcurrent.decrementAndGet();
            permits.release();
        }
    }

    /**
     * Non-blocking variant: if no permit is available within the
     * timeout, fail fast instead of queuing indefinitely — appropriate
     * when the caller has its own fallback (Phase 7's backup gateway) and
     * would rather fail over immediately than wait behind a saturated
     * limiter.
     */
    public <T> T tryCallWithLimit(java.util.concurrent.Callable<T> gatewayCall,
                                   long timeout, TimeUnit unit) throws Exception {
        if (!permits.tryAcquire(timeout, unit)) {
            rejectedCount.incrementAndGet();
            throw new RateLimitExceededException("no permit available within " + timeout + " " + unit);
        }
        try {
            int now = currentConcurrent.incrementAndGet();
            peakConcurrent.updateAndGet(prev -> Math.max(prev, now));
            return gatewayCall.call();
        } finally {
            currentConcurrent.decrementAndGet();
            permits.release();
        }
    }

    public int availablePermits() {
        return permits.availablePermits();
    }

    public int peakConcurrent() {
        return peakConcurrent.get();
    }

    public int rejectedCount() {
        return rejectedCount.get();
    }

    public static final class RateLimitExceededException extends Exception {
        public RateLimitExceededException(String message) { super(message); }
    }
}
