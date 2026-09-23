package com.orderengine.phase2;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Version 2: ReentrantReadWriteLock. Splits the single monitor into a
 * read lock (shared — any number of readers may hold it concurrently) and
 * a write lock (exclusive — one writer at a time, and no readers while a
 * writer holds it). This directly targets the throughput problem in
 * InventoryLedgerSynchronized: checkAvailability() calls from different
 * threads can now proceed in parallel.
 *
 * Correctness guarantee: identical to the synchronized version. Acquiring
 * either lock establishes the same happens-before relationship as a
 * monitor lock/unlock (JLS 17.4.5) — a writer's release happens-before the
 * next reader's or writer's acquire. You get the visibility guarantee
 * without losing it, only reduced contention among readers.
 *
 * Two things worth knowing about ReentrantReadWriteLock specifically:
 *
 * 1. Fairness policy. `new ReentrantReadWriteLock(true)` gives FIFO
 *    ordering: threads acquire in roughly the order they arrived, which
 *    prevents writer starvation but costs throughput (no barging allowed).
 *    The default `false` (non-fair) allows barging — a thread requesting
 *    the lock can jump ahead of threads already waiting — which is faster
 *    in the uncontended/lightly-contended case but under sustained heavy
 *    read load, a writer can be starved indefinitely, because there's
 *    always another reader ready to barge in before the writer's turn.
 *    Below we use the fair variant deliberately: reserve() are the writes
 *    that actually take money-equivalent action (decrementing real stock),
 *    and starving those under read pressure is a correctness-adjacent risk
 *    we don't want, even at some throughput cost. This is a genuine
 *    engineering trade-off, not a default you should copy blindly.
 *
 * 2. Lock downgrading is supported (acquire write lock, then read lock,
 *    then release write lock, while still holding the read lock) — useful
 *    when a writer needs to keep reading consistent state immediately
 *    after its own write without letting another writer in between. Lock
 *    UPGRADING (read -> write) is NOT supported and will deadlock a
 *    single thread against itself if attempted — release the read lock
 *    fully before acquiring the write lock.
 */
public class InventoryLedgerReadWriteLock implements InventoryLedger {

    private final Map<String, Integer> stock;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); // fair, see javadoc above

    public InventoryLedgerReadWriteLock(Map<String, Integer> initialStock) {
        this.stock = new HashMap<>(initialStock);
    }

    @Override
    public int checkAvailability(String productId) {
        rwLock.readLock().lock();
        try {
            return stock.getOrDefault(productId, 0);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public boolean reserve(String productId, int qty) {
        rwLock.writeLock().lock();
        try {
            int current = stock.getOrDefault(productId, 0);
            if (current < qty) {
                return false;
            }
            stock.put(productId, current - qty);
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void release(String productId, int qty) {
        rwLock.writeLock().lock();
        try {
            stock.merge(productId, qty, Integer::sum);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
