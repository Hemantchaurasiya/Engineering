package com.orderengine.phase10;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Exchanger;

/**
 * Exchanger is the narrowest, least commonly needed synchronizer in this
 * phase, but is exactly right for one specific shape: TWO threads that
 * repeatedly need to swap a whole object with each other at a rendezvous
 * point — the classic case being DOUBLE BUFFERING. A producer thread
 * fills buffer A with newly-validated orders while a consumer thread
 * drains buffer B (the PREVIOUS batch) into, say, a batch database
 * write. When the producer finishes filling A and the consumer finishes
 * draining B, they meet at exchange() and SWAP buffers — producer now
 * fills what was B (now empty), consumer now drains what was A (now
 * full) — with neither thread ever touching a buffer the other is
 * actively using at the same time, and no buffer ever sitting idle while
 * one side waits for the other, since they hand off directly.
 *
 * exchange() blocks until BOTH threads have called it, then returns each
 * thread the OTHER thread's object — a single atomic swap point. This is
 * meaningfully different from a BlockingQueue-based handoff: with a
 * queue, the producer can get arbitrarily far ahead of the consumer
 * (bounded by capacity) without ever synchronizing on a shared point in
 * time; Exchanger forces a hard, mutual rendezvous every round — useful
 * specifically when the two sides genuinely need to alternate in
 * lockstep rather than being decoupled by a buffer.
 */
public class DoubleBufferExchangerDemo {

    private static final int BATCH_SIZE = 5;
    private static final int BATCH_COUNT = 4;

    public static void main(String[] args) throws InterruptedException {
        Exchanger<List<Long>> exchanger = new Exchanger<>();

        Thread producer = new Thread(() -> {
            List<Long> buffer = new ArrayList<>();
            long orderId = 1;
            try {
                for (int batch = 0; batch < BATCH_COUNT; batch++) {
                    buffer.clear();
                    for (int i = 0; i < BATCH_SIZE; i++) {
                        buffer.add(orderId++);
                        sleep(20); // simulate time spent validating/producing each order
                    }
                    System.out.println("  producer: batch " + (batch + 1) + " filled with " + buffer
                            + ", exchanging...");
                    buffer = exchanger.exchange(buffer); // hands off the FULL buffer, receives an empty one back
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            List<Long> buffer = new ArrayList<>(); // starts EMPTY — nothing to drain on round 0
            try {
                for (int batch = 0; batch < BATCH_COUNT; batch++) {
                    buffer = exchanger.exchange(buffer); // hands off the (now-empty, post-drain) buffer, receives the full one
                    System.out.println("  consumer: received batch " + (batch + 1) + " = " + buffer + ", draining...");
                    // "drain" = simulate a batch database write
                    sleep(BATCH_SIZE * 20L);
                    buffer.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println("\nAll " + BATCH_COUNT + " batches exchanged and drained.");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
