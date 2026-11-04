package com.orderengine.phase5;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Failed payment-gateway calls (transient network errors, gateway
 * timeouts) get scheduled for retry with exponential backoff instead of
 * hammering the gateway immediately — DelayQueue is the natural fit: a
 * blocking queue where take() only returns an element once its delay has
 * expired, ordered by expiry time, so a single consumer thread can just
 * loop calling take() and it will only ever receive tasks that are
 * actually ready to run, in the order they became ready.
 *
 * Internally, DelayQueue is a PriorityQueue (min-heap ordered by
 * getDelay()) guarded by a single lock, plus a "leader-follower"
 * optimization: rather than every waiting consumer thread waking up on
 * every head-of-queue change, only one designated "leader" thread waits
 * with a bounded timeout for the head element's exact remaining delay;
 * other consumers wait unboundedly until leadership passes to them. This
 * avoids the thundering-herd of every consumer waking up and immediately
 * re-checking on every insertion, at the cost of a small amount of
 * internal coordination complexity you get for free from the JDK.
 */
public class RetryScheduler {

    public static final class RetryTask implements Delayed {
        final long orderId;
        final int attemptNumber;
        private final long readyAtNanos;

        RetryTask(long orderId, int attemptNumber, long delayMillis) {
            this.orderId = orderId;
            this.attemptNumber = attemptNumber;
            this.readyAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long remaining = readyAtNanos - System.nanoTime();
            return unit.convert(remaining, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            // Required for the internal PriorityQueue ordering — MUST be
            // consistent with getDelay(), not some unrelated field, or
            // the heap ordering (and therefore take() order) is wrong.
            return Long.compare(this.readyAtNanos, ((RetryTask) other).readyAtNanos);
        }

        @Override
        public String toString() {
            return "RetryTask{orderId=" + orderId + ", attempt=" + attemptNumber + "}";
        }
    }

    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_DELAY_MILLIS = 200;

    private final DelayQueue<RetryTask> delayQueue = new DelayQueue<>();
    private final AtomicInteger scheduledCount = new AtomicInteger(0);
    private final AtomicInteger exhaustedCount = new AtomicInteger(0);

    /** Schedules a retry with exponential backoff: 200ms, 400ms, 800ms, 1600ms, 3200ms. */
    public boolean scheduleRetry(long orderId, int previousAttemptNumber) {
        int nextAttempt = previousAttemptNumber + 1;
        if (nextAttempt > MAX_ATTEMPTS) {
            exhaustedCount.incrementAndGet();
            return false; // caller should now route to a dead-letter / manual-review path
        }
        long delayMillis = BASE_DELAY_MILLIS * (1L << (nextAttempt - 1)); // 200 * 2^(n-1)
        delayQueue.put(new RetryTask(orderId, nextAttempt, delayMillis));
        scheduledCount.incrementAndGet();
        return true;
    }

    /** Blocks until a retry is actually due, then returns it. */
    public RetryTask takeReadyRetry() throws InterruptedException {
        return delayQueue.take();
    }

    public int pendingCount() {
        return delayQueue.size();
    }

    public int scheduledCount() {
        return scheduledCount.get();
    }

    public int exhaustedCount() {
        return exhaustedCount.get();
    }
}
