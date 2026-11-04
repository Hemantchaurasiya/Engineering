package com.orderengine.phase2;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Throughput comparison across the three InventoryLedger implementations
 * under a workload shaped like real inventory-service traffic: 95% reads
 * (checkAvailability, e.g. product page views / cart checks) and 5%
 * writes (reserve/release, e.g. actual checkouts and cancellations).
 *
 * Each implementation runs the identical workload for a fixed duration
 * across a fixed thread count and reports total operations completed —
 * higher is better. Run with a thread count comfortably above your core
 * count to see contention effects; a laptop with 8 logical cores should
 * use at least 16-32 threads to make the difference visible.
 */
public class LedgerBenchmark {

    private static final int PRODUCT_COUNT = 50;
    private static final int THREADS = 32;
    private static final long DURATION_MILLIS = 2000;
    private static final double READ_RATIO = 0.95;

    public static void main(String[] args) throws InterruptedException {
        Map<String, Integer> initialStock = seedStock();

        System.out.println("Workload: " + THREADS + " threads, " + DURATION_MILLIS
                + "ms each, " + (int) (READ_RATIO * 100) + "% reads / "
                + (int) ((1 - READ_RATIO) * 100) + "% writes, " + PRODUCT_COUNT + " products");
        System.out.println();

        long synchronizedOps = run("synchronized (coarse monitor)",
                new InventoryLedgerSynchronized(initialStock));
        long rwLockOps = run("ReentrantReadWriteLock (fair)",
                new InventoryLedgerReadWriteLock(initialStock));
        long stampedOps = run("StampedLock (optimistic read)",
                new InventoryLedgerStamped(initialStock));

        System.out.println();
        System.out.println("Relative throughput (synchronized = 1.00x):");
        System.out.printf("  synchronized : 1.00x (%,d ops)%n", synchronizedOps);
        System.out.printf("  RW lock      : %.2fx (%,d ops)%n",
                (double) rwLockOps / synchronizedOps, rwLockOps);
        System.out.printf("  StampedLock  : %.2fx (%,d ops)%n",
                (double) stampedOps / synchronizedOps, stampedOps);
    }

    private static long run(String label, InventoryLedger ledger) throws InterruptedException {
        AtomicLong totalOps = new AtomicLong();
        Thread[] threads = new Thread[THREADS];
        long deadline = System.currentTimeMillis() + DURATION_MILLIS;

        for (int t = 0; t < THREADS; t++) {
            threads[t] = new Thread(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                long ops = 0;
                while (System.currentTimeMillis() < deadline) {
                    String productId = "product-" + rnd.nextInt(PRODUCT_COUNT);
                    if (rnd.nextDouble() < READ_RATIO) {
                        ledger.checkAvailability(productId);
                    } else if (rnd.nextBoolean()) {
                        ledger.reserve(productId, 1);
                    } else {
                        ledger.release(productId, 1);
                    }
                    ops++;
                }
                totalOps.addAndGet(ops);
            }, "bench-" + label + "-" + t);
        }

        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        long total = totalOps.get();
        System.out.printf("%-32s %,12d ops in %dms (%,d ops/sec)%n",
                label, total, DURATION_MILLIS, total * 1000 / DURATION_MILLIS);
        return total;
    }

    private static Map<String, Integer> seedStock() {
        Map<String, Integer> stock = new HashMap<>();
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            stock.put("product-" + i, 1_000_000); // large enough not to run dry mid-benchmark
        }
        return stock;
    }
}
