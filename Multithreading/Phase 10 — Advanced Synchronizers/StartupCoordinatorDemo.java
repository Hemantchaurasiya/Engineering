package com.orderengine.phase10;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class StartupCoordinatorDemo {

    public static void main(String[] args) throws InterruptedException {
        String[] stages = {"validation", "inventory-reservation", "payment", "notification", "sink"};
        StartupCoordinator coordinator = new StartupCoordinator(stages.length);
        AtomicLong[] startTimes = new AtomicLong[stages.length];

        Thread[] threads = new Thread[stages.length];
        long baseline = System.currentTimeMillis();

        for (int i = 0; i < stages.length; i++) {
            final String stageName = stages[i];
            final int idx = i;
            startTimes[i] = new AtomicLong();
            threads[i] = new Thread(() -> {
                // Simulate each stage taking a different amount of time to initialize.
                sleep(ThreadLocalRandom.current().nextLong(50, 400));
                coordinator.signalStageReady(stageName);
                try {
                    coordinator.awaitStart(stageName);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                startTimes[idx].set(System.currentTimeMillis() - baseline);
            }, stageName + "-init");
        }

        for (Thread t : threads) t.start();

        // Coordinator thread: waits for all 5 to report ready, then releases everyone together.
        boolean started = coordinator.startWhenAllReady(5);

        for (Thread t : threads) t.join();

        System.out.println("\nstarted=" + started);
        System.out.println("Time (ms after baseline) each stage actually began work:");
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (int i = 0; i < stages.length; i++) {
            long t = startTimes[i].get();
            System.out.printf("  %-24s %dms%n", stages[i], t);
            min = Math.min(min, t);
            max = Math.max(max, t);
        }
        System.out.println("\nspread between earliest and latest start: " + (max - min)
                + "ms (should be very small — all stages were released by the SAME countDown() call, "
                + "regardless of how differently long each took to initialize beforehand)");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
