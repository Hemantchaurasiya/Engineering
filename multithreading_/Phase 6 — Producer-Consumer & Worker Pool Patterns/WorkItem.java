package com.orderengine.phase6;

/**
 * Every item flowing through a pipeline queue is either real work (Data)
 * or a shutdown signal (PoisonPill) — modeled as a sealed interface so
 * every consumer is FORCED by the compiler (via exhaustive switch pattern
 * matching) to handle both cases, rather than relying on a magic null or
 * a separate out-of-band "stop" flag that a worker could forget to check.
 *
 * This is "the poison pill pattern": instead of signaling shutdown
 * through some external mutable flag a worker has to remember to poll
 * (exactly the Phase 1 visibility-bug shape if done wrong, and even done
 * "right" with volatile, still a signal that's INDEPENDENT of the actual
 * work queue — a worker could observe the flag between processing items
 * and stop with items still sitting unprocessed in the queue), a
 * shutdown signal is pushed through the SAME queue as real work. Because
 * BlockingQueue guarantees FIFO delivery (for the non-priority queue
 * types), every item enqueued before the poison pill is guaranteed to be
 * taken and processed before a worker ever sees the pill — shutdown
 * cleanly drains everything already queued, with no separate
 * synchronization needed between "check the flag" and "check the queue".
 */
public sealed interface WorkItem<T> permits WorkItem.Data, WorkItem.PoisonPill {

    record Data<T>(T value) implements WorkItem<T> {}

    final class PoisonPill<T> implements WorkItem<T> {
        @SuppressWarnings("rawtypes")
        private static final PoisonPill INSTANCE = new PoisonPill();

        private PoisonPill() {}

        @SuppressWarnings("unchecked")
        public static <T> PoisonPill<T> instance() {
            return (PoisonPill<T>) INSTANCE;
        }
    }

    static <T> WorkItem<T> of(T value) {
        return new Data<>(value);
    }
}
