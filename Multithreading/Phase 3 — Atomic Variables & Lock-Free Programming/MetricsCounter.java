package com.orderengine.phase3;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Throughput counters for the engine's live metrics dashboard (this gets
 * wired into real HTTP/JMX exposure in Phase 13 — for now, just the
 * counting primitive itself). Every pipeline stage increments counters
 * like "orders validated", "orders reserved", "payments attempted" on
 * every single order — this is about as hot and high-contention as a
 * counter gets in this whole engine, since EVERY worker thread across
 * EVERY stage touches these on EVERY order.
 *
 * This class exposes both an AtomicLong-backed and a LongAdder-backed
 * counter side by side so the benchmark in this phase can measure the
 * real difference, and so the doc can point at concrete code instead of
 * an abstract claim.
 *
 * Why LongAdder exists and what it trades away:
 *
 * AtomicLong.incrementAndGet() is a single CAS loop on ONE shared memory
 * location (see OrderIdGenerator.nextIdManualCas() for the exact
 * mechanics). Under LOW contention this is excellent — often faster than
 * a lock. Under HIGH contention (many threads all incrementing the same
 * AtomicLong at once, exactly this dashboard's traffic shape) it degrades
 * badly: every thread's CAS attempt invalidates every other thread's
 * cached copy of that memory location (cache-line ping-pong across
 * cores), so most attempts fail and retry, and retries themselves cause
 * more invalidation. Throughput can fall well below what raw core count
 * would suggest is achievable.
 *
 * LongAdder fixes this by NOT maintaining one shared counter. Internally
 * it holds a base value plus an array of "Cells" — separate padded
 * counter slots. Under contention, different threads get striped across
 * different cells (via a per-thread hash), so most increments land on
 * DIFFERENT memory locations and don't invalidate each other's caches at
 * all. sum() (or intValue()/longValue()) adds up the base plus every
 * cell on demand.
 *
 * The trade-off: sum() is NOT a cheap, instantaneous, linearizable read
 * the way AtomicLong.get() is. It walks every cell and adds them up,
 * and — critically — it is not atomic as a whole: if increments are
 * happening concurrently with a sum() call, the result can be (very
 * slightly) stale relative to the exact real-time count, because there's
 * no single moment "the sum" is taken across cells. For a live metrics
 * counter that's periodically scraped for a dashboard, this is a
 * complete non-issue — a dashboard being off by a handful of counts for
 * one scrape interval under heavy concurrent write load is invisible.
 * For a counter feeding a business decision that needs an exact,
 * point-in-time consistent value (Phase 2's inventory stock, for
 * example — you cannot let "sum was a little stale" oversell real
 * inventory), LongAdder is the WRONG tool and AtomicLong/locking is
 * correct despite being slower. This distinction — hot approximate
 * counters vs. exact business-critical counters — is the single most
 * important takeaway of this class.
 */
public class MetricsCounter {

    private final AtomicLong atomicCount = new AtomicLong(0);
    private final LongAdder adderCount = new LongAdder();

    public void incrementAtomic() {
        atomicCount.incrementAndGet();
    }

    public void incrementAdder() {
        adderCount.increment();
    }

    public long atomicValue() {
        return atomicCount.get();
    }

    public long adderValue() {
        return adderCount.sum();
    }
}
