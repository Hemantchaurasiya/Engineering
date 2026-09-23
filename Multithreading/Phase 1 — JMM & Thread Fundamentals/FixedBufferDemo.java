package com.orderengine.phase1;

import java.util.concurrent.CountDownLatch;

public class FixedBufferDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Fixed Demo 1: visibility (volatile running) ===");
        runVisibilityDemo();

        System.out.println();
        System.out.println("=== Fixed Demo 2: atomicity (AtomicInteger count) ===");
        runAtomicityDemo();
    }

    private static void runVisibilityDemo() throws InterruptedException {
        OrderIngestionBuffer buffer = new OrderIngestionBuffer();
        CountDownLatch drainStarted = new CountDownLatch(1);

        Thread drainThread = new Thread(() -> {
            drainStarted.countDown();
            buffer.drainUntilShutdown(null);
            System.out.println("  drain thread observed shutdown and exited.");
        }, "drain-thread-fixed");
        drainThread.setDaemon(true);
        drainThread.start();
        drainStarted.await();

        Thread.sleep(300);
        System.out.println("  control thread calling shutdown()...");
        buffer.shutdown();

        drainThread.join(4000);
        System.out.println("  RESULT: " + (drainThread.isAlive()
                ? "STILL BROKEN (unexpected!)"
                : "correctly exited within timeout — volatile fix works."));
    }

    private static void runAtomicityDemo() throws InterruptedException {
        OrderIngestionBuffer buffer = new OrderIngestionBuffer();
        int threads = 8;
        int incrementsPerThread = 100_000;
        int expected = threads * incrementsPerThread;

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                for (int i = 0; i < incrementsPerThread; i++) {
                    buffer.offer(Order.of(i, "cust", 1.0));
                }
            });
        }
        for (Thread w : workers) w.start();
        for (Thread w : workers) w.join();

        int actual = buffer.getCount();
        System.out.println("  expected count = " + expected);
        System.out.println("  actual count   = " + actual);
        System.out.println("  RESULT: " + (actual == expected
                ? "no lost updates — AtomicInteger fix works."
                : "STILL BROKEN (unexpected!)"));
    }
}
