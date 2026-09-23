package com.orderengine.phase9;

/**
 * Stands in for a real outbound call (email/SMS/push provider API) —
 * dominated by network wait, negligible actual CPU work, exactly
 * Phase 4's I/O-bound profile that originally justified sizing the
 * `notification` pool larger than a CPU-bound one. This phase asks: with
 * virtual threads, do we even need that sizing exercise anymore for
 * work shaped like this?
 */
public class NotificationSender {

    private final long simulatedLatencyMillis;

    public NotificationSender(long simulatedLatencyMillis) {
        this.simulatedLatencyMillis = simulatedLatencyMillis;
    }

    /** A genuinely blocking call — Thread.sleep stands in for a blocking network read. */
    public void send(long orderId) {
        try {
            Thread.sleep(simulatedLatencyMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
