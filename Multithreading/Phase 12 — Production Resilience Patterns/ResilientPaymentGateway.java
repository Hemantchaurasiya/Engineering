package com.orderengine.phase12;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The real payoff of this phase: all three patterns wrapped around one
 * call, IN A DELIBERATE ORDER, each protecting against a different
 * failure shape:
 *
 *   1. RATE LIMITER first — cheapest check, rejects pure overload before
 *      spending any other resource (a thread, a permit) on a call that's
 *      going to be rejected anyway. Checking this first means an
 *      over-limit burst never even touches the bulkhead or circuit
 *      breaker's bookkeeping.
 *
 *   2. CIRCUIT BREAKER second — if the dependency is KNOWN to be
 *      unhealthy, reject immediately without spending a bulkhead thread
 *      on a call that's very likely to fail or hang anyway. Checking
 *      this before the bulkhead means a known outage doesn't consume
 *      bulkhead capacity that healthy retries (once the circuit
 *      eventually closes again) will need.
 *
 *   3. BULKHEAD last — the actual call happens on the dependency's own
 *      isolated pool, so even if the first two checks passed (rate
 *      limiter had capacity, circuit breaker was CLOSED/HALF_OPEN) and
 *      the call turns out to be slow or hangs, it can only ever consume
 *      THIS dependency's own dedicated resources, never anyone else's.
 *
 * Every rejection path (rate limit, open circuit, bulkhead timeout) ends
 * up in ONE fallback, so the caller always gets a definite outcome
 * rather than needing to handle three different exception types
 * differently — mirroring Phase 7's PaymentOrchestrator.handle() as the
 * single guaranteed terminal step, just synchronous here instead of
 * CompletableFuture-based.
 */
public class ResilientPaymentGateway {

    private final FlakyPaymentGateway realGateway;
    private final TokenBucketRateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    private final AtomicLong rateLimitedCount = new AtomicLong(0);
    private final AtomicLong circuitRejectedCount = new AtomicLong(0);
    private final AtomicLong bulkheadFailedCount = new AtomicLong(0);
    private final AtomicLong succeededCount = new AtomicLong(0);
    private final AtomicLong fallbackCount = new AtomicLong(0);

    public ResilientPaymentGateway(FlakyPaymentGateway realGateway) {
        this.realGateway = realGateway;
        this.rateLimiter = new TokenBucketRateLimiter(50, 30); // burst up to 50, steady-state 30/sec
        this.circuitBreaker = new CircuitBreaker(20, 0.5, 1000); // trip at 50% failures over last 20 calls, 1s cooldown
        this.bulkhead = new Bulkhead("payment-gateway", 10); // isolated pool, separate from any other dependency's
    }

    public String charge(long orderId) {
        if (!rateLimiter.tryAcquire()) {
            rateLimitedCount.incrementAndGet();
            return fallback(orderId, "rate limited");
        }

        try {
            String result = circuitBreaker.call(() ->
                    bulkhead.call(() -> realGateway.charge(orderId), 500, TimeUnit.MILLISECONDS));
            succeededCount.incrementAndGet();
            return result;
        } catch (CircuitBreaker.CircuitOpenException e) {
            circuitRejectedCount.incrementAndGet();
            return fallback(orderId, "circuit open");
        } catch (Exception e) {
            bulkheadFailedCount.incrementAndGet();
            return fallback(orderId, "call failed/timed out: " + e.getMessage());
        }
    }

    private String fallback(long orderId, String reason) {
        fallbackCount.incrementAndGet();
        // A real fallback might queue for later retry, or route to a
        // backup gateway (Phase 7's PaymentOrchestrator) — kept simple
        // here since the point of this phase is the protective wrapping,
        // not re-deriving Phase 7's fallback chain.
        return "FALLBACK[" + reason + "]-order-" + orderId;
    }

    public void printStats() {
        System.out.println("  succeeded=" + succeededCount.get()
                + " rateLimited=" + rateLimitedCount.get()
                + " circuitRejected=" + circuitRejectedCount.get()
                + " bulkheadFailed=" + bulkheadFailedCount.get()
                + " totalFallbacks=" + fallbackCount.get()
                + " circuitState=" + circuitBreaker.state());
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.state();
    }

    public void shutdown() {
        bulkhead.shutdown();
    }
}
