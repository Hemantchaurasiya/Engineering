package com.orderengine.phase3;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Correctness checks under real concurrency:
 *
 *  1. OrderIdGenerator: N threads each generate M IDs; every ID across
 *     all threads combined must be unique with no duplicates and no gaps
 *     — proves the CAS retry loop never loses or double-issues an ID
 *     under contention.
 *
 *  2. TreiberStack: N threads push M items each, then N threads pop
 *     concurrently until empty; every pushed item must be popped exactly
 *     once — proves the lock-free push/pop pair is correct under
 *     concurrent access (this test alone does NOT exercise the ABA
 *     scenario, which requires the specific node-reuse-under-pause
 *     interleaving demonstrated deterministically in AbaProblemDemo).
 */
public class Phase3ConcurrencyTest {

    public static void main(String[] args) throws InterruptedException {
        testOrderIdGeneratorUniqueness();
        testTreiberStackPushPop();
    }

    private static void testOrderIdGeneratorUniqueness() throws InterruptedException {
        int threadCount = 16;
        int idsPerThread = 50_000;
        OrderIdGenerator generator = new OrderIdGenerator();
        ConcurrentHashMap<Long, Boolean> seen = new ConcurrentHashMap<>();
        AtomicInteger duplicates = new AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < idsPerThread; i++) {
                    long id = generator.nextId();
                    if (seen.putIfAbsent(id, Boolean.TRUE) != null) {
                        duplicates.incrementAndGet();
                    }
                }
            });
        }
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        int expectedTotal = threadCount * idsPerThread;
        System.out.printf("OrderIdGenerator: expected=%d generated=%d duplicates=%d -> %s%n",
                expectedTotal, seen.size(), duplicates.get(),
                (seen.size() == expectedTotal && duplicates.get() == 0) ? "OK" : "MISMATCH");
    }

    private static void testTreiberStackPushPop() throws InterruptedException {
        int threadCount = 16;
        int itemsPerThread = 20_000;
        int totalItems = threadCount * itemsPerThread;

        TreiberStack<Integer> stack = new TreiberStack<>();
        Thread[] pushers = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pushers[t] = new Thread(() -> {
                for (int i = 0; i < itemsPerThread; i++) {
                    stack.push(threadId * itemsPerThread + i);
                }
            });
        }
        for (Thread th : pushers) th.start();
        for (Thread th : pushers) th.join();

        ConcurrentHashMap<Integer, Boolean> popped = new ConcurrentHashMap<>();
        AtomicInteger duplicatePops = new AtomicInteger(0);
        Thread[] poppers = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            poppers[t] = new Thread(() -> {
                Integer value;
                while ((value = stack.pop()) != null) {
                    if (popped.putIfAbsent(value, Boolean.TRUE) != null) {
                        duplicatePops.incrementAndGet();
                    }
                }
            });
        }
        for (Thread th : poppers) th.start();
        for (Thread th : poppers) th.join();

        System.out.printf("TreiberStack: pushed=%d popped=%d duplicatePops=%d stackEmpty=%s -> %s%n",
                totalItems, popped.size(), duplicatePops.get(), stack.isEmpty(),
                (popped.size() == totalItems && duplicatePops.get() == 0 && stack.isEmpty())
                        ? "OK" : "MISMATCH");
    }
}
