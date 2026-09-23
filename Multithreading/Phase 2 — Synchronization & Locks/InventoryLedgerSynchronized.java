package com.orderengine.phase2;

import java.util.HashMap;
import java.util.Map;

/**
 * Version 1: correct, and the obvious first implementation. A single
 * intrinsic lock (the object's own monitor) guards every operation,
 * including checkAvailability(), which never mutates anything.
 *
 * This is NOT a bug in the sense Phase 1's demos were — there is no race
 * condition here, no lost update, no visibility hole. Every operation is
 * correctly mutually exclusive and correctly visible (the monitor lock
 * happens-before rule, Phase 1 §4.2, covers both).
 *
 * The problem is a THROUGHPUT problem, not a correctness problem: given
 * the read-heavy traffic shape described on InventoryLedger, this
 * implementation serializes concurrent read-only checkAvailability() calls
 * against each other for no reason — two threads just reading stock for
 * different (or even the same) product must still take turns, one at a
 * time, even though neither is mutating anything. Under N reader threads
 * this scales like a single thread, not like N.
 */
public class InventoryLedgerSynchronized implements InventoryLedger {

    private final Map<String, Integer> stock;

    public InventoryLedgerSynchronized(Map<String, Integer> initialStock) {
        this.stock = new HashMap<>(initialStock);
    }

    @Override
    public synchronized int checkAvailability(String productId) {
        return stock.getOrDefault(productId, 0);
    }

    @Override
    public synchronized boolean reserve(String productId, int qty) {
        int current = stock.getOrDefault(productId, 0);
        if (current < qty) {
            return false;
        }
        stock.put(productId, current - qty);
        return true;
    }

    @Override
    public synchronized void release(String productId, int qty) {
        stock.merge(productId, qty, Integer::sum);
    }
}
