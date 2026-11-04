package com.orderengine.phase10;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PaymentRateLimiterDemo {

    public static void main(String[] args) throws InterruptedException {
        int maxConcurrent = 5;
        int totalCalls = 100;
        PaymentRateLimiter limiter = new PaymentRateLimiter(maxConcurrent);
        CountDownLatch done = new CountDownLatch(totalCalls);

        System.out.println("Firing " + totalCalls + " concurrent gateway calls through a limiter capped at "
                + maxConcurrent + " concurrent in-flight calls...\n");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalCalls; i++) {
                final long orderId = i;
                executor.submit(() -> {
                    try {
                        limiter.callWithLimit(() -> {
                            Thread.sleep(50); // simulated gateway call
                            return orderId;
                        });
                    } catch (Exception e) {
                        System.out.println("  call failed: " + e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await(30, TimeUnit.SECONDS);
        }

        System.out.println("peak concurrent in-flight calls observed: " + limiter.peakConcurrent()
                + " (configured cap: " + maxConcurrent + ")");
        System.out.println("RESULT: " + (limiter.peakConcurrent() <= maxConcurrent
                ? "OK — the semaphore held firm even with " + totalCalls + " calls fired essentially at once "
                        + "via cheap virtual threads (Phase 9) with no natural thread-count limit of their own"
                : "BUG — peak exceeded the configured cap"));
        System.out.println("available permits after all calls finished: " + limiter.availablePermits()
                + " (should equal " + maxConcurrent + " — every acquire() was matched by a release())");
    }
}
