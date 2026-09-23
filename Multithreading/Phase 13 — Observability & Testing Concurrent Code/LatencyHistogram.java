package com.orderengine.phase13;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

/**
 * A minimal latency histogram: keeps the last N samples in a fixed-size
 * ring buffer and computes p50/p95/p99/max on demand from a SORTED COPY
 * of that ring. Uses Phase 2's StampedLock — writes (record()) are
 * frequent (once per completed call across the whole engine) and reads
 * (snapshot(), for the dashboard) are comparatively rare, exactly the
 * read-light/write-heavy inverse of Phase 2's InventoryLedger case,
 * still solvable with the same tool: snapshot() takes the real write
 * lock (it needs a fully consistent copy of the ring to sort), while
 * record() takes the write lock too, since it mutates the ring — this
 * histogram doesn't actually have a genuine "many concurrent readers"
 * shape the way InventoryLedger did, so there's no optimistic-read
 * benefit to chase here; StampedLock is used simply as a plain mutual-
 * exclusion lock in this case, which is a perfectly valid, if slightly
 * generic-feeling, use of it.
 *
 * HONESTY NOTE, consistent with every prior phase's benchmark
 * disclaimers: this is NOT how a production metrics library actually
 * computes percentiles at scale — real implementations use proper
 * streaming histogram algorithms (e.g. HDRHistogram) that don't require
 * storing and sorting raw samples, which becomes both memory- and
 * CPU-expensive at high cardinality/volume. This implementation is
 * intentionally simple specifically to keep the concurrency mechanics
 * visible rather than buried under a proper histogram algorithm's own
 * complexity — production code should use a real, well-tested
 * percentile-estimation library instead of this class.
 */
public class LatencyHistogram {

    private static final int RING_SIZE = 1000;

    private final StampedLock lock = new StampedLock();
    private final long[] samples = new long[RING_SIZE];
    private int writeIndex = 0;
    private int count = 0; // how many slots have ever been filled, capped at RING_SIZE

    public void record(long millis) {
        long stamp = lock.writeLock();
        try {
            samples[writeIndex] = millis;
            writeIndex = (writeIndex + 1) % RING_SIZE;
            count = Math.min(RING_SIZE, count + 1);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public Snapshot snapshot() {
        long[] copy;
        int n;
        long stamp = lock.writeLock(); // needs a fully consistent copy to sort correctly
        try {
            n = count;
            copy = Arrays.copyOf(samples, n);
        } finally {
            lock.unlockWrite(stamp);
        }

        if (n == 0) {
            return Snapshot.EMPTY;
        }
        Arrays.sort(copy);
        return new Snapshot(
                percentile(copy, 0.50),
                percentile(copy, 0.95),
                percentile(copy, 0.99),
                copy[n - 1],
                n);
    }

    private static long percentile(long[] sorted, double p) {
        int index = (int) Math.ceil(p * sorted.length) - 1;
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index];
    }

    public record Snapshot(long p50, long p95, long p99, long max, int sampleCount) {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0);
    }
}
