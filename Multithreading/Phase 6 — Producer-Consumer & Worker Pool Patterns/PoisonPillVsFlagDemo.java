package com.orderengine.phase6;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proves, with a real (deterministic, latch-coordinated) scenario, why
 * "push a poison pill through the queue" is the correct shutdown
 * technique and "flip an external flag the worker polls" is not — not a
 * style preference, a genuine correctness gap.
 *
 * FLAG-BASED SHUTDOWN: a worker loop shaped like
 *   while (running.get()) { Item item = queue.poll(...); if (item != null) process(item); }
 * has a real race: shutdown() can flip `running` to false at ANY point,
 * including the instant AFTER a producer just enqueued several items but
 * BEFORE the worker's next poll() call. The worker's next loop check sees
 * running == false and exits — with those items still sitting in the
 * queue, unprocessed, forever (or until some other, separate mechanism
 * bothers to drain them, which nothing here does). Whether this happens
 * on a given run is a timing race — sometimes shutdown() happens to
 * align nicely, sometimes it strands work — exactly the kind of
 * intermittent, hard-to-reproduce-in-testing bug Phase 1 covered for
 * plain visibility flags, at a higher structural level.
 *
 * POISON-PILL SHUTDOWN: shutdown() enqueues a sentinel value THROUGH THE
 * SAME QUEUE. Because the queue is FIFO, every item enqueued before the
 * pill is guaranteed to be taken and processed before the worker ever
 * sees the pill — there is no separate "check a flag" step with its own
 * independent race window; the shutdown signal and the work share one
 * ordering guarantee (the queue's).
 *
 * This demo forces the losing interleaving for the flag-based version on
 * purpose (via a controlled handoff) rather than relying on timing luck,
 * to make the bug reproduce every single run.
 */
public class PoisonPillVsFlagDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Flag-based shutdown: can strand queued work ===");
        runFlagBasedScenario();

        System.out.println("\n=== Poison-pill shutdown: guarantees full drain ===");
        runPoisonPillScenario();
    }

    private static void runFlagBasedScenario() throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(100);
        java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
        AtomicInteger processed = new AtomicInteger(0);
        CountDownLatch workerAboutToCheckFlag = new CountDownLatch(1);
        CountDownLatch producerDone = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            while (true) {
                // Simulate the worker having just finished its last item
                // and being about to loop back and check `running` —
                // signal that we're at exactly this vulnerable point.
                workerAboutToCheckFlag.countDown();
                await(producerDone); // wait until the producer has enqueued more work behind our back

                if (!running.get()) {
                    break; // exits WITHOUT checking the queue at all
                }
                Integer item = queue.poll();
                if (item != null) {
                    processed.incrementAndGet();
                }
            }
        });

        worker.start();
        await(workerAboutToCheckFlag);

        // Producer enqueues 5 items, THEN shutdown flips the flag — all
        // classic "should still get processed" work, submitted before
        // shutdown was ever requested.
        for (int i = 0; i < 5; i++) {
            queue.put(i);
        }
        running.set(false);
        producerDone.countDown();

        worker.join();
        System.out.println("  items enqueued before shutdown: 5, processed: " + processed.get());
        System.out.println("  RESULT: " + (processed.get() < 5
                ? "BUG REPRODUCED — " + (5 - processed.get()) + " item(s) left stranded in the queue, "
                        + "silently dropped by shutdown"
                : "no items stranded this run"));
        System.out.println("  items still sitting in the queue, unprocessed: " + queue.size());
    }

    private static void runPoisonPillScenario() throws InterruptedException {
        BlockingQueue<WorkItem<Integer>> queue = new ArrayBlockingQueue<>(100);
        AtomicInteger processed = new AtomicInteger(0);

        Thread worker = new Thread(() -> {
            while (true) {
                WorkItem<Integer> item;
                try {
                    item = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (item instanceof WorkItem.PoisonPill) {
                    break;
                }
                processed.incrementAndGet();
            }
        });
        worker.start();

        for (int i = 0; i < 5; i++) {
            queue.put(WorkItem.of(i));
        }
        queue.put(WorkItem.PoisonPill.instance()); // shutdown signal goes through the SAME queue, after the work

        worker.join();
        System.out.println("  items enqueued before shutdown: 5, processed: " + processed.get());
        System.out.println("  RESULT: " + (processed.get() == 5
                ? "correct — all 5 items processed before the pill was ever seen"
                : "unexpected mismatch"));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
