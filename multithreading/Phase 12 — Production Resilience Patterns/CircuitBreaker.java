package com.orderengine.phase12;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A circuit breaker stops calling a struggling downstream dependency
 * ENTIRELY for a while, instead of letting every caller keep hitting it
 * (and keep paying its full timeout) while it's known to be failing.
 * This is a fundamentally different problem from rate limiting or
 * bulkheading: those control HOW MUCH traffic reaches a dependency;
 * a circuit breaker controls WHETHER traffic reaches it AT ALL, based
 * on that dependency's own recent observed health.
 *
 * THREE STATES:
 *
 *   CLOSED (normal operation): calls pass through to the real
 *   dependency. Failures are tracked in a sliding window of the last N
 *   outcomes. If the failure rate within that window exceeds the
 *   configured threshold, the breaker TRIPS to OPEN.
 *
 *   OPEN (tripped — dependency presumed unhealthy): calls are REJECTED
 *   IMMEDIATELY, without ever reaching the real dependency — this is
 *   the entire point: stop sending traffic to something that's known to
 *   be failing, both to protect the CALLER (fail fast instead of
 *   waiting out a slow timeout on every single call) and the
 *   DEPENDENCY (stop piling more load onto something already
 *   struggling, which is often exactly what makes a partial outage turn
 *   into a total one). After a configured cooldown period elapses, the
 *   breaker transitions to HALF_OPEN.
 *
 *   HALF_OPEN (cautious probe): exactly ONE trial call is allowed
 *   through to test whether the dependency has recovered. If it
 *   succeeds, the breaker closes (resumes normal operation, resets
 *   failure tracking). If it fails, the breaker reopens and the
 *   cooldown timer restarts. Critically, only ONE probe is allowed at a
 *   time in this state — letting many concurrent callers all "test" the
 *   recovering dependency simultaneously would recreate exactly the
 *   pile-on problem OPEN was protecting against, right at the most
 *   fragile moment (a service that JUST started recovering).
 *
 * This implementation uses a simple sliding window of the last N call
 * outcomes (a fixed-size ring of booleans, not a full historical log)
 * rather than a strict time-based window, for simplicity — production
 * circuit breaker libraries (e.g. resilience4j) offer both count-based
 * and time-based windows; the state-machine logic covered here is the
 * same either way.
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int windowSize;
    private final double failureRateThreshold; // 0.0-1.0
    private final long cooldownMillis;

    private final boolean[] outcomes; // true = success, tracked as a ring buffer
    private int outcomeIndex = 0;
    private int outcomeCount = 0; // how many slots have been filled at least once

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicLong openedAtMillis = new AtomicLong(0);
    private final AtomicInteger halfOpenProbeInFlight = new AtomicInteger(0); // 0 or 1 — guards the single-probe rule

    private final Object windowLock = new Object();

    public CircuitBreaker(int windowSize, double failureRateThreshold, long cooldownMillis) {
        this.windowSize = windowSize;
        this.failureRateThreshold = failureRateThreshold;
        this.cooldownMillis = cooldownMillis;
        this.outcomes = new boolean[windowSize];
    }

    public State state() {
        return state.get();
    }

    /**
     * Runs the given call through the breaker: rejects immediately if
     * OPEN (and cooldown hasn't elapsed), allows exactly one probe if
     * HALF_OPEN, otherwise calls through and records the outcome.
     */
    public <T> T call(Callable<T> operation) throws Exception {
        State current = state.get();

        if (current == State.OPEN) {
            if (System.currentTimeMillis() - openedAtMillis.get() >= cooldownMillis) {
                // Cooldown elapsed — attempt to transition to HALF_OPEN.
                // CAS ensures only ONE thread among many concurrent
                // callers actually performs this transition.
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    current = State.HALF_OPEN;
                } else {
                    current = state.get(); // another thread already transitioned it
                }
            } else {
                throw new CircuitOpenException("circuit is OPEN — rejecting call without attempting it");
            }
        }

        if (current == State.HALF_OPEN) {
            // Enforce the single-probe rule: only the first thread to
            // reach here gets to actually try the call; everyone else is
            // rejected until the probe resolves.
            if (!halfOpenProbeInFlight.compareAndSet(0, 1)) {
                throw new CircuitOpenException("circuit is HALF_OPEN and a probe is already in flight — rejecting");
            }
            try {
                T result = operation.call();
                recordOutcome(true);
                state.set(State.CLOSED); // probe succeeded — fully close
                resetWindow();
                return result;
            } catch (Exception e) {
                recordOutcome(false);
                openCircuit(); // probe failed — reopen, cooldown restarts
                throw e;
            } finally {
                halfOpenProbeInFlight.set(0);
            }
        }

        // CLOSED — normal path.
        try {
            T result = operation.call();
            recordOutcome(true);
            return result;
        } catch (Exception e) {
            recordOutcome(false);
            checkIfShouldTrip();
            throw e;
        }
    }

    private void recordOutcome(boolean success) {
        synchronized (windowLock) {
            outcomes[outcomeIndex] = success;
            outcomeIndex = (outcomeIndex + 1) % windowSize;
            outcomeCount = Math.min(windowSize, outcomeCount + 1);
        }
    }

    private void checkIfShouldTrip() {
        double failureRate;
        synchronized (windowLock) {
            if (outcomeCount < windowSize) {
                return; // not enough data yet to judge — avoids tripping on a tiny, unrepresentative sample
            }
            int failures = 0;
            for (boolean outcome : outcomes) {
                if (!outcome) failures++;
            }
            failureRate = (double) failures / windowSize;
        }
        if (failureRate >= failureRateThreshold) {
            openCircuit();
        }
    }

    private void openCircuit() {
        state.set(State.OPEN);
        openedAtMillis.set(System.currentTimeMillis());
    }

    private void resetWindow() {
        synchronized (windowLock) {
            outcomeIndex = 0;
            outcomeCount = 0;
        }
    }

    public static final class CircuitOpenException extends Exception {
        public CircuitOpenException(String message) { super(message); }
    }
}
