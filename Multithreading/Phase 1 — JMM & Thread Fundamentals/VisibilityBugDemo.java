package com.orderengine.phase1;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reproduces both bugs in BrokenOrderIngestionBuffer against a hard
 * wall-clock timeout, so the demo terminates and prints a verdict either
 * way instead of hanging your terminal.
 *
 * Run with the SAME command shown in the phase notes — the JIT needs to
 * actually kick in (C2 compilation) for bug #1 to reproduce reliably, so
 * this must run as a normal `java` invocation, not single-stepped in a
 * debugger, and the loop body must be trivial (no println) so nothing
 * accidentally introduces a memory barrier.
 */
public class VisibilityBugDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Demo 1: visibility bug (running flag) ===");
        runVisibilityDemo();

        System.out.println();
        System.out.println("=== Demo 2: lost-update bug (count++) ===");
        runLostUpdateDemo();
    }

    private static void runVisibilityDemo() throws InterruptedException {
        BrokenOrderIngestionBuffer buffer = new BrokenOrderIngestionBuffer();
        CountDownLatch drainStarted = new CountDownLatch(1);

        Thread drainThread = new Thread(() -> {
            drainStarted.countDown();
            buffer.drainUntilShutdown(() -> {
                // proves the loop is alive and spinning, not deadlocked
                System.out.println("  drain thread still spinning...");
            });
            System.out.println("  drain thread OBSERVED shutdown and exited normally.");
        }, "drain-thread");

        drainThread.setDaemon(true);
        drainThread.start();
        drainStarted.await();

        Thread.sleep(300); // let the drain loop warm up and get JIT-compiled
        System.out.println("  control thread calling shutdown()...");
        buffer.shutdown();

        drainThread.join(4000); // wait up to 4s
        if (drainThread.isAlive()) {
            System.out.println("  RESULT: BUG REPRODUCED — drain thread never saw the "
                    + "shutdown write after 4000ms. It is still spinning on a stale "
                    + "cached value of `running`.");
        } else {
            System.out.println("  RESULT: drain thread exited (bug did not reproduce this "
                    + "run — JIT hoisting is timing/hardware dependent, see notes on why "
                    + "this is exactly the problem with relying on it 'usually working').");
        }
    }

    private static void runLostUpdateDemo() throws InterruptedException {
        BrokenOrderIngestionBuffer buffer = new BrokenOrderIngestionBuffer();
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
        if (actual != expected) {
            System.out.println("  RESULT: BUG REPRODUCED — lost " + (expected - actual)
                    + " increments (" + String.format("%.2f", 100.0 * (expected - actual) / expected)
                    + "% of updates silently disappeared).");
        } else {
            System.out.println("  RESULT: no updates lost this run (still not thread-safe — "
                    + "just got lucky with scheduling; rerun to see it fail).");
        }
    }
}
