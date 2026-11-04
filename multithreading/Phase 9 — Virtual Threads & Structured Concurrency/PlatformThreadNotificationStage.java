package com.orderengine.phase9;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The Phase 6-style approach: a FIXED, deliberately-sized pool of real
 * platform (OS) threads. Sizing this pool requires Phase 4's I/O-bound
 * formula (Ncpu * U * (1 + W/C)) — too small and throughput suffers
 * (only N notifications truly in flight at once, everything else
 * queues); too large and you pay real OS-thread cost (each platform
 * thread reserves a real OS stack, by default ~1MB on most JVMs/OSes) for
 * threads that spend nearly all their time simply blocked waiting, doing
 * nothing useful with that reserved memory or the OS scheduling overhead
 * of tracking them.
 */
public class PlatformThreadNotificationStage {

    private final ExecutorService pool;
    private final NotificationSender sender;

    public PlatformThreadNotificationStage(int poolSize, NotificationSender sender) {
        this.pool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "notification-platform-worker");
            t.setDaemon(true);
            return t;
        });
        this.sender = sender;
    }

    public void sendAll(List<Long> orderIds) throws InterruptedException {
        List<Future<?>> futures = new ArrayList<>();
        for (long orderId : orderIds) {
            futures.add(pool.submit(() -> sender.send(orderId)));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdown() throws InterruptedException {
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
    }
}
