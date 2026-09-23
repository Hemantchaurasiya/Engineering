package com.orderengine.phase7;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.orderengine.phase7.Domain.OrderOutcome;

/**
 * Runs many orders through PaymentOrchestrator concurrently and tallies
 * outcomes. The primary gateway is configured with a real chance of
 * being slow enough to trip orTimeout AND a real chance of throwing —
 * both fallback paths (timeout -> backup, failure -> backup) get
 * exercised across enough orders to see all four terminal states:
 * COMPLETED via primary, COMPLETED via backup (after timeout or
 * failure), HELD_FOR_REVIEW (fraud check flagged it), and FAILED (both
 * gateways failed for the same order — rare, since it requires two
 * independent low-probability failures to coincide, but the demo runs
 * enough orders that it should appear at least occasionally).
 */
public class PaymentOrchestratorDemo {

    public static void main(String[] args) throws InterruptedException {
        PaymentGatewayClient primary = new PaymentGatewayClient("primary-gateway", 8, 150, 0.15);
        // primary: 150ms base latency + jitter can exceed the 300ms
        // orTimeout some of the time, plus a 15% simulated hard failure
        // rate — both fallback triggers get real exercise.
        PaymentGatewayClient backup = new PaymentGatewayClient("backup-gateway", 8, 80, 0.05);
        FraudCheckService fraudCheck = new FraudCheckService();

        PaymentOrchestrator orchestrator = new PaymentOrchestrator(primary, backup, fraudCheck);

        int orderCount = 200;
        ConcurrentHashMap<String, AtomicInteger> statusTally = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, AtomicInteger> gatewayTally = new ConcurrentHashMap<>();

        long start = System.currentTimeMillis();
        List<CompletableFuture<OrderOutcome>> futures = new java.util.ArrayList<>();
        for (long orderId = 1; orderId <= orderCount; orderId++) {
            futures.add(orchestrator.processPayment(orderId, 49.99));
        }

        // allOf: wait for every in-flight order's full async chain to
        // complete before tallying — a common real pattern for "wait for
        // a batch of independent async operations, then proceed."
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allDone.join();
        long elapsed = System.currentTimeMillis() - start;

        for (CompletableFuture<OrderOutcome> f : futures) {
            OrderOutcome outcome = f.join(); // safe: allOf already guaranteed every future is complete
            statusTally.computeIfAbsent(outcome.finalStatus(), k -> new AtomicInteger()).incrementAndGet();
            if (outcome.payment() != null) {
                gatewayTally.computeIfAbsent(outcome.payment().gatewayName(), k -> new AtomicInteger()).incrementAndGet();
            }
        }

        System.out.println(orderCount + " orders processed concurrently in " + elapsed + "ms\n");
        System.out.println("Final status breakdown:");
        statusTally.forEach((status, count) -> System.out.printf("  %-16s %d%n", status, count.get()));
        System.out.println("\nGateway that ultimately handled each successful charge:");
        gatewayTally.forEach((gateway, count) -> System.out.printf("  %-16s %d%n", gateway, count.get()));
    }
}
