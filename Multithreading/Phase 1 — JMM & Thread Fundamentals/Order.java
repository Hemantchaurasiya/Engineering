package com.orderengine.phase1;

/**
 * Minimal immutable order record used across all phases of the engine.
 * Immutability matters here for a concurrency reason, not just style:
 * a fully-constructed immutable object, once safely published, requires
 * no further synchronization to read from other threads (JMM final-field
 * semantics guarantee this — see 01-happens-before-and-jmm.md, section 5).
 */
public record Order(long orderId, String customerId, double amount, long createdAtNanos) {

    public static Order of(long orderId, String customerId, double amount) {
        return new Order(orderId, customerId, amount, System.nanoTime());
    }
}
