package com.orderengine.phase6;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Makes backpressure directly observable rather than just asserted: a
 * single slow consumer (simulating a downstream stage overwhelmed or a
 * slow external dependency) against several fast producers on a small
 * bounded queue. Watch the printed queue depth climb to capacity and
 * STAY there — that plateau is producers being blocked inside put(),
 * throttled to the consumer's actual rate, not a bug or a stall.
 *
 * Contrast this with what an UNBOUNDED queue would do under the exact
 * same mismatched rates: queue depth would climb without limit for as
 * long as producers keep producing, with producers never blocking at
 * all — exactly Phase 5 §3's PriorityBlockingQueue danger, generalized:
 * an unbounded buffer doesn't prevent overload, it just hides it as
 * growing memory until something (usually the JVM's heap) finally gives
 * out, far removed in time and stack trace from the actual cause.
 */
public class BoundedBufferBackpressureDemo {

    private static final int QUEUE_CAPACITY = 20;
    private static final int PRODUCER_THREADS = 4;
    private static final int ITEMS_PER_PRODUCER = 500;

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong produced = new AtomicLong(0);
        AtomicLong consumed = new AtomicLong(0);

        // One deliberately slow consumer.
        Thread consumer = new Thread(() -> {
            while (running.get() || !queue.isEmpty()) {
                try {
                    Integer item = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (item != null) {
                        Thread.sleep(15); // simulate slow downstream work
                        consumed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "slow-consumer");

        // Several fast producers.
        Thread[] producers = new Thread[PRODUCER_THREADS];
        for (int p = 0; p < PRODUCER_THREADS; p++) {
            producers[p] = new Thread(() -> {
                for (int i = 0; i < ITEMS_PER_PRODUCER; i++) {
                    try {
                        queue.put(i); // BLOCKS here once the queue is full — this is backpressure
                        produced.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "fast-producer");
        }

        // A monitor thread printing queue depth every 200ms so the
        // plateau-at-capacity is directly visible.
        Thread monitor = new Thread(() -> {
            while (running.get() || !queue.isEmpty()) {
                System.out.printf("  queueDepth=%2d/%d  produced=%-5d consumed=%-5d%n",
                        queue.size(), QUEUE_CAPACITY, produced.get(), consumed.get());
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "monitor");

        consumer.start();
        monitor.start();
        for (Thread p : producers) p.start();
        for (Thread p : producers) p.join();

        System.out.println("  all producers finished submitting (they were repeatedly BLOCKED in "
                + "put() whenever the queue was full, exactly matching their rate to the consumer)");

        running.set(false);
        consumer.join();
        monitor.join();

        System.out.println("\n  final: produced=" + produced.get() + " consumed=" + consumed.get()
                + " -> " + (produced.get() == consumed.get() ? "OK, none lost" : "MISMATCH"));
    }
}
