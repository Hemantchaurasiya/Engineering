package com.orderengine.phase3;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * High-contention increment throughput comparison: many threads
 * incrementing a single shared counter as fast as possible, no other
 * work — this is deliberately the worst case for AtomicLong (maximum
 * cache-line contention on one memory location) and the best case for
 * LongAdder (contention gets striped across cells). Run with a thread
 * count comfortably above your core count to see the gap widen; with
 * thread count == 1 (no contention at all) expect them to be roughly
 * equal or AtomicLong slightly ahead, since LongAdder has a small amount
 * of extra indirection that only pays off once there's real contention
 * to stripe away.
 */
public class CounterBenchmark {

    private static final long INCREMENTS_PER_THREAD = 5_000_000;

    public static void main(String[] args) throws InterruptedException {
        for (int threads : new int[] {1, 4, 16, 32}) {
            System.out.println("--- " + threads + " thread(s), "
                    + INCREMENTS_PER_THREAD + " increments each ---");
            long atomicMillis = benchmarkAtomicLong(threads);
            long adderMillis = benchmarkLongAdder(threads);
            System.out.printf("  AtomicLong : %,6dms%n", atomicMillis);
            System.out.printf("  LongAdder  : %,6dms  (%.2fx %s)%n",
                    adderMillis,
                    (double) atomicMillis / Math.max(adderMillis, 1),
                    adderMillis <= atomicMillis ? "faster" : "slower");
            System.out.println();
        }
    }

    private static long benchmarkAtomicLong(int threadCount) throws InterruptedException {
        AtomicLong counter = new AtomicLong(0);
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                for (long i = 0; i < INCREMENTS_PER_THREAD; i++) {
                    counter.incrementAndGet();
                }
            });
        }
        long start = System.currentTimeMillis();
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();
        long elapsed = System.currentTimeMillis() - start;

        long expected = (long) threadCount * INCREMENTS_PER_THREAD;
        if (counter.get() != expected) {
            System.out.println("  WARNING: AtomicLong count mismatch! expected=" + expected
                    + " actual=" + counter.get());
        }
        return elapsed;
    }

    private static long benchmarkLongAdder(int threadCount) throws InterruptedException {
        LongAdder counter = new LongAdder();
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                for (long i = 0; i < INCREMENTS_PER_THREAD; i++) {
                    counter.increment();
                }
            });
        }
        long start = System.currentTimeMillis();
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();
        long elapsed = System.currentTimeMillis() - start;

        long expected = (long) threadCount * INCREMENTS_PER_THREAD;
        if (counter.sum() != expected) {
            System.out.println("  WARNING: LongAdder count mismatch! expected=" + expected
                    + " actual=" + counter.sum());
        }
        return elapsed;
    }
}
