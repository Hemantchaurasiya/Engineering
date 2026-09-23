package com.orderengine.phase7;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import com.orderengine.phase7.Domain.FraudCheckResult;

/**
 * An independent async call with NO data dependency on the payment
 * gateway charge — this is deliberately the "run two unrelated async
 * calls concurrently, then combine both results" case that thenCombine
 * exists for, as opposed to thenCompose's "this call's result is needed
 * as INPUT to the next call" case (PaymentOrchestrator uses both,
 * distinctly, for exactly this reason — see phase notes §3).
 */
public class FraudCheckService {

    private final Executor executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "fraud-check-worker");
        t.setDaemon(true);
        return t;
    });

    public CompletableFuture<FraudCheckResult> check(long orderId, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(60); // simulated call to a fraud-scoring service
            int riskScore = ThreadLocalRandom.current().nextInt(0, 100);
            boolean approved = riskScore < 90; // 10% simulated flagged-as-risky rate
            return new FraudCheckResult(orderId, approved, riskScore);
        }, executor);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
