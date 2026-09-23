package com.orderengine.phase7;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import com.orderengine.phase7.Domain.GatewayException;
import com.orderengine.phase7.Domain.PaymentResult;

/**
 * Stands in for a real outbound HTTP call to a payment gateway. The
 * critical design point here, not the simulated business logic: every
 * async call is submitted via supplyAsync(supplier, EXECUTOR) with an
 * EXPLICIT executor argument — never the single-argument overload.
 *
 * The single-argument CompletableFuture.supplyAsync(supplier) (and every
 * other *Async method without an explicit Executor) runs on
 * ForkJoinPool.commonPool() by default — a single JVM-wide shared pool
 * ALSO used by parallel streams and, in some environments, other
 * libraries' internal async work. Submitting a blocking I/O call (exactly
 * what a real payment gateway call is) to that shared pool without
 * realizing it can starve completely unrelated code elsewhere in the
 * same JVM that happens to also need the common pool at the same moment
 * — a genuinely surprising, hard-to-diagnose production interaction
 * between two features that look unrelated in the code. See
 * AsyncPitfallsDemo for this proven directly. This class instead uses
 * its own dedicated executor, sized the same way Phase 4's `payment`
 * pool was (I/O-bound: heavily wait-dominated relative to compute).
 */
public class PaymentGatewayClient {

    private final String gatewayName;
    private final Executor executor;
    private final long simulatedLatencyMillis;
    private final double simulatedFailureRate;

    public PaymentGatewayClient(String gatewayName, int poolSize,
                                 long simulatedLatencyMillis, double simulatedFailureRate) {
        this.gatewayName = gatewayName;
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, gatewayName + "-worker");
            t.setDaemon(true);
            return t;
        });
        this.simulatedLatencyMillis = simulatedLatencyMillis;
        this.simulatedFailureRate = simulatedFailureRate;
    }

    public CompletableFuture<PaymentResult> charge(long orderId, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(simulatedLatencyMillis);
            if (ThreadLocalRandom.current().nextDouble() < simulatedFailureRate) {
                throw new GatewayException(gatewayName + " rejected order " + orderId
                        + " (simulated gateway failure)");
            }
            String txnId = gatewayName + "-txn-" + orderId + "-" + ThreadLocalRandom.current().nextInt(100000);
            return new PaymentResult(orderId, txnId, gatewayName);
        }, executor); // <-- explicit executor, never the shared common pool for real I/O work
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
