package com.orderengine.phase5;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * VIP orders (a real e-commerce concept — premium/priority customers,
 * time-critical restock orders, etc.) should be pulled off the front of
 * the ingestion queue ahead of regular orders, regardless of arrival
 * order. PriorityBlockingQueue is the natural fit: an unbounded, blocking
 * priority heap — take() blocks when empty, but elements come out in
 * priority order (as defined by the given Comparator) rather than FIFO.
 *
 * THE CRITICAL GOTCHA, and why this class exists as a wrapper instead of
 * exposing PriorityBlockingQueue directly: it is UNBOUNDED. Unlike
 * ArrayBlockingQueue/bounded LinkedBlockingQueue, put() on a
 * PriorityBlockingQueue NEVER BLOCKS and NEVER REJECTS — it always
 * succeeds and grows the internal array as needed. That means a
 * PriorityBlockingQueue provides ZERO natural backpressure: if consumers
 * fall behind producers, the queue simply grows without limit until the
 * JVM runs out of heap. This is a genuinely common production incident
 * shape — "the priority queue kept growing during a traffic spike, OOM
 * a few hours later" — precisely because the class looks and behaves
 * like a BlockingQueue (blocking take()) and it's easy to assume it also
 * blocks on put() like ArrayBlockingQueue does. It does not.
 *
 * This wrapper adds an explicit Semaphore-based capacity limit so
 * offer()/put() DO apply real backpressure, while still getting
 * PriorityBlockingQueue's priority ordering on the way out.
 *
 * Also worth knowing: elements with EQUAL priority have no guaranteed
 * relative ordering (PriorityBlockingQueue is not stable) — two regular
 * (non-VIP) orders can come out in either order relative to each other
 * even if one arrived first. If strict FIFO-within-priority-tier matters,
 * the Comparator needs a secondary tiebreaker (e.g. sequence number/
 * arrival timestamp), which is exactly what this class's Comparator does.
 */
public class VipAwareOrderQueue {

    public record PrioritizedOrder(long orderId, boolean vip, long sequenceNumber) {}

    private static final Comparator<PrioritizedOrder> PRIORITY_ORDER =
            Comparator.comparing((PrioritizedOrder o) -> !o.vip())      // VIP (false negated -> true first) sorts first
                    .thenComparingLong(PrioritizedOrder::sequenceNumber); // tie-break: arrival order within a tier

    private final PriorityBlockingQueue<PrioritizedOrder> queue =
            new PriorityBlockingQueue<>(64, PRIORITY_ORDER);
    private final Semaphore capacity;

    public VipAwareOrderQueue(int maxCapacity) {
        this.capacity = new Semaphore(maxCapacity);
    }

    /** Blocks (applies real backpressure) if the queue is at capacity. */
    public void put(PrioritizedOrder order) throws InterruptedException {
        capacity.acquire();
        queue.put(order); // never actually blocks internally, but keeps the BlockingQueue contract explicit
    }

    /** Non-blocking; returns false instead of growing past capacity if full. */
    public boolean offer(PrioritizedOrder order, long timeout, TimeUnit unit) throws InterruptedException {
        if (!capacity.tryAcquire(timeout, unit)) {
            return false;
        }
        queue.put(order);
        return true;
    }

    public PrioritizedOrder take() throws InterruptedException {
        PrioritizedOrder order = queue.take(); // blocks until an element is available
        capacity.release();
        return order;
    }

    public int size() {
        return queue.size();
    }
}
