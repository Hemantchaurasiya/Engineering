package com.orderengine.phase2;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

/**
 * Version 3: StampedLock with optimistic reads. Goes a step further than
 * ReentrantReadWriteLock: for the common case (no writer active right
 * now), a reader does NOT take any lock at all — no CAS, no memory
 * barrier beyond a plain volatile-like read of the stamp. It reads the
 * data, then validates that no write happened in between. Only if
 * validation fails does it fall back to a real (pessimistic) read lock.
 *
 * This matters at scale because even the "shared" read lock in
 * ReentrantReadWriteLock has real cost under high read concurrency: every
 * reader still does an atomic increment/decrement of a shared reader
 * count on acquire/release, which becomes a cache-line contention point
 * across cores as reader count grows. StampedLock's optimistic mode
 * removes that shared mutable state from the hot path entirely.
 *
 * Three lock modes StampedLock provides:
 *  - writeLock() — exclusive, like ReentrantReadWriteLock's write lock.
 *  - readLock() — pessimistic shared read, like ReentrantReadWriteLock's
 *    read lock (a real lock, blocks writers, has the reader-count
 *    contention cost above).
 *  - tryOptimisticRead() — no blocking, no real lock acquired at all;
 *    returns a stamp to validate afterward with stamp.validate(stamp).
 *
 * Critical correctness rule for optimistic reads: you must NEVER act on
 * data read during the optimistic window as if it were final — including
 * throwing exceptions, indexing into arrays with it, or (most relevant
 * here) making a business decision like "is there enough stock" without
 * validating first. The pattern is always: read into local variables,
 * validate(), and only trust those locals if validate() returned true.
 * If it returns false, retry with a real read lock — do not just retry
 * the optimistic read in a spin loop indefinitely without a fallback, or
 * a sufficiently active writer can starve the reader forever.
 *
 * IMPORTANT: unlike ReentrantLock/synchronized/ReentrantReadWriteLock,
 * StampedLock is NOT reentrant. A thread that already holds the write
 * lock and calls writeLock() again will deadlock against itself. None of
 * our methods here nest lock acquisitions, so this doesn't bite us, but
 * it's the single most common StampedLock production bug — recursive
 * helper methods that also try to lock.
 */
public class InventoryLedgerStamped implements InventoryLedger {

    private final Map<String, Integer> stock;
    private final StampedLock lock = new StampedLock();

    public InventoryLedgerStamped(Map<String, Integer> initialStock) {
        this.stock = new HashMap<>(initialStock);
    }

    @Override
    public int checkAvailability(String productId) {
        long stamp = lock.tryOptimisticRead();
        int value = stock.getOrDefault(productId, 0);

        if (!lock.validate(stamp)) {
            // A write happened during our read — the optimistic read is
            // unusable. Fall back to a real, blocking read lock.
            stamp = lock.readLock();
            try {
                value = stock.getOrDefault(productId, 0);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return value;
    }

    @Override
    public boolean reserve(String productId, int qty) {
        long stamp = lock.writeLock();
        try {
            int current = stock.getOrDefault(productId, 0);
            if (current < qty) {
                return false;
            }
            stock.put(productId, current - qty);
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void release(String productId, int qty) {
        long stamp = lock.writeLock();
        try {
            stock.merge(productId, qty, Integer::sum);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}
