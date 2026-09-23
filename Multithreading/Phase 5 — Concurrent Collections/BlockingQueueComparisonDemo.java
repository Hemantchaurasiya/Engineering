package com.orderengine.phase5;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ArrayBlockingQueue and LinkedBlockingQueue look interchangeable from
 * their shared BlockingQueue interface, but have a real internal
 * difference that matters under concurrent producer/consumer load:
 *
 * ArrayBlockingQueue: backed by a fixed-size circular array, allocated
 * up front at the configured capacity. Uses a SINGLE ReentrantLock
 * guarding both put and take, with two Conditions (notEmpty, notFull) on
 * that one lock. This means a producer calling put() and a consumer
 * calling take() AT THE SAME INSTANT still contend for the same single
 * lock — one must wait for the other, even though conceptually adding to
 * one end and removing from the other shouldn't need to block each other.
 *
 * LinkedBlockingQueue: backed by linked nodes, optionally bounded (an
 * unbounded LinkedBlockingQueue is a real footgun — see phase notes §3,
 * this class always constructs it with an explicit capacity). Uses TWO
 * SEPARATE locks — putLock and takeLock — so a producer and a consumer
 * can genuinely proceed concurrently: one thread adding a node at the
 * tail while another thread removes a node at the head don't contend for
 * the same lock at all. The count is tracked with an AtomicInteger
 * (visible to and coordinated between both lock-protected paths) rather
 * than being protected by either single lock. This two-lock design is
 * why LinkedBlockingQueue generally outperforms ArrayBlockingQueue
 * specifically under concurrent MIXED put/take load — pure production-
 * only or pure consumption-only workloads don't benefit from the split
 * since only one lock is ever contended anyway.
 *
 * This benchmark runs matched producer/consumer thread counts against
 * both queue types under identical load and reports throughput.
 */
public class BlockingQueueComparisonDemo {

    private static final int PRODUCERS = 8;
    private static final int CONSUMERS = 8;
    private static final int ITEMS_PER_PRODUCER = 200_000;
    private static final int QUEUE_CAPACITY = 1000;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("ArrayBlockingQueue (single lock for put+take):");
        runBenchmark(new ArrayBlockingQueue<>(QUEUE_CAPACITY));

        System.out.println("\nLinkedBlockingQueue (separate putLock/takeLock):");
        runBenchmark(new LinkedBlockingQueue<>(QUEUE_CAPACITY));
    }

    private static void runBenchmark(BlockingQueue<Integer> queue) throws InterruptedException {
        int totalItems = PRODUCERS * ITEMS_PER_PRODUCER;
        AtomicLong consumed = new AtomicLong(0);

        Thread[] producers = new Thread[PRODUCERS];
        for (int p = 0; p < PRODUCERS; p++) {
            producers[p] = new Thread(() -> {
                for (int i = 0; i < ITEMS_PER_PRODUCER; i++) {
                    try {
                        queue.put(i);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
        }

        Thread[] consumers = new Thread[CONSUMERS];
        for (int c = 0; c < CONSUMERS; c++) {
            consumers[c] = new Thread(() -> {
                while (consumed.get() < totalItems) {
                    try {
                        Integer item = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (item != null) {
                            consumed.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
        }

        long start = System.currentTimeMillis();
        for (Thread c : consumers) c.start();
        for (Thread p : producers) p.start();
        for (Thread p : producers) p.join();
        for (Thread c : consumers) c.join();
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("  %,d items through %d producers / %d consumers in %,dms (%,d items/sec)%n",
                totalItems, PRODUCERS, CONSUMERS, elapsed, elapsed == 0 ? 0 : totalItems * 1000L / elapsed);
    }
}
