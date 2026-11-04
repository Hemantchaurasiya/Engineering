package com.orderengine.phase2;

/**
 * Contract shared by every InventoryLedger implementation in this phase.
 * inventory-service sits behind order-service: every order first calls
 * checkAvailability() (read), and a confirmed order calls reserve() (write).
 * In production traffic shapes, availability checks vastly outnumber
 * reservations — browsing/cart-add traffic dwarfs actual checkout traffic.
 * That read:write skew is exactly what motivates this phase's progression
 * from synchronized -> ReadWriteLock -> StampedLock.
 */
public interface InventoryLedger {

    /** Read-only: current stock for a product. Called far more than reserve(). */
    int checkAvailability(String productId);

    /**
     * Write: attempts to reserve {@code qty} units. Returns true and
     * decrements stock if enough was available; returns false (no state
     * change) otherwise.
     */
    boolean reserve(String productId, int qty);

    /** Write: returns stock to the pool, e.g. on order cancellation. */
    void release(String productId, int qty);
}
