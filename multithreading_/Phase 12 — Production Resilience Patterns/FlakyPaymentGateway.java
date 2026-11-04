package com.orderengine.phase12;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A payment gateway simulator whose health can be changed AT RUNTIME —
 * ChaosTestHarness dials this between healthy, slow, and failing
 * profiles mid-run to simulate a realistic partial-outage-then-recovery
 * pattern, and observes how ResilientPaymentGateway's composed
 * protections behave through each phase.
 */
public class FlakyPaymentGateway {

    public enum Health { HEALTHY, SLOW, FAILING }

    private final AtomicReference<Health> health = new AtomicReference<>(Health.HEALTHY);

    public void setHealth(Health newHealth) {
        health.set(newHealth);
    }

    public String charge(long orderId) throws Exception {
        Health current = health.get();
        switch (current) {
            case HEALTHY -> {
                Thread.sleep(30); // fast, normal call
                return "txn-" + orderId;
            }
            case SLOW -> {
                Thread.sleep(800); // still eventually succeeds, just slowly
                return "txn-" + orderId;
            }
            case FAILING -> {
                Thread.sleep(20);
                throw new RuntimeException("simulated gateway failure for order " + orderId);
            }
            default -> throw new IllegalStateException();
        }
    }
}
