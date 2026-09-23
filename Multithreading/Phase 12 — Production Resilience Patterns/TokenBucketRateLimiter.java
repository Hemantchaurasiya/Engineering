package com.orderengine.phase12;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Rate limiting by THROUGHPUT (calls per second, with bounded bursting),
 * as distinct from Phase 10's Semaphore-based PaymentRateLimiter, which
 * caps CONCURRENT in-flight calls. These are genuinely different
 * constraints a real payment gateway can impose simultaneously: "no more
 * than 20 concurrent connections" (Phase 10's concern) AND "no more than
 * 100 requests per second, even if they're all fast and none overlap"
 * (this class's concern). A service can violate the rate limit while
 * having very few calls in flight at once, if it just fires requests too
 * frequently — the two limiters catch different failure shapes and are
 * meant to be composed together, not treated as interchangeable.
 *
 * TOKEN BUCKET ALGORITHM: a bucket holds up to `capacity` tokens.
 * Tokens refill continuously at `refillRatePerSecond`. Each call
 * consumes one token; if none are available, the call is rejected (or,
 * in a blocking variant, waits). The bucket's capacity allows BURSTS —
 * if no calls have happened for a while, tokens accumulate up to the
 * cap, and a sudden burst of calls can be served immediately up to that
 * cap before falling back to the steady refill rate — unlike a naive
 * "fixed calls per fixed time window" limiter, which can either allow
 * a full window's quota to burst instantly at the window boundary
 * (a real bug in naive fixed-window limiters) or be overly strict
 * right after a window resets.
 *
 * LAZY REFILL: rather than running a background thread/scheduled task
 * to add tokens periodically (extra thread, extra complexity, a whole
 * additional thing that could itself misbehave), this implementation
 * computes how many tokens SHOULD have accumulated based on elapsed
 * wall-clock time, right at the moment of each acquire() call. This is
 * a standard, simpler technique: no background work at all when the
 * limiter isn't being used, and no clock-alignment issues from a
 * separately-scheduled refill task drifting relative to actual elapsed
 * time.
 */
public class TokenBucketRateLimiter {

    private final ReentrantLock lock = new ReentrantLock();
    private final double capacity;
    private final double refillRatePerSecond;
    private double availableTokens;
    private long lastRefillNanos;

    public TokenBucketRateLimiter(double capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.availableTokens = capacity; // start full — allow an initial burst
        this.lastRefillNanos = System.nanoTime();
    }

    /** Non-blocking: returns true and consumes a token if one is available, false otherwise. */
    public boolean tryAcquire() {
        lock.lock();
        try {
            refill();
            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        double newTokens = elapsedSeconds * refillRatePerSecond;
        if (newTokens > 0) {
            availableTokens = Math.min(capacity, availableTokens + newTokens);
            lastRefillNanos = now;
        }
    }

    public double availableTokens() {
        lock.lock();
        try {
            refill();
            return availableTokens;
        } finally {
            lock.unlock();
        }
    }
}
