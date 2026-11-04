package com.orderengine.phase7;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.orderengine.phase7.Domain.FraudCheckResult;
import com.orderengine.phase7.Domain.OrderOutcome;
import com.orderengine.phase7.Domain.PaymentResult;

/**
 * The real async orchestration for this phase: charge the primary
 * payment gateway, fall back to a backup gateway on timeout or failure,
 * run an independent fraud check CONCURRENTLY with the charge attempt
 * (not after it — no reason to wait for one before starting the other),
 * and combine everything into one final outcome, with every failure path
 * handled explicitly rather than left to propagate as an uncaught
 * exception on whatever thread happens to be running when it surfaces.
 *
 * Every combinator used here is deliberate, not interchangeable with the
 * others — see the phase notes for the full explanation of each:
 *
 *   .orTimeout(...)              — bounds how long we wait on the primary
 *                                   gateway before giving up on it
 *   .exceptionallyCompose(...)   — recovers from EITHER a real gateway
 *                                   failure OR the orTimeout's
 *                                   TimeoutException by falling back to
 *                                   an ENTIRELY NEW async call (the
 *                                   backup gateway) — NOT just a
 *                                   fallback VALUE, which is what plain
 *                                   exceptionally() would be limited to
 *   .thenCombine(...)            — merges the (now-resolved, one way or
 *                                   another) payment result with the
 *                                   INDEPENDENTLY, CONCURRENTLY running
 *                                   fraud check — thenCombine is for two
 *                                   futures with NO dependency on each
 *                                   other's result, run in parallel
 *   .thenCompose(...)            — used implicitly inside the fallback
 *                                   path: chaining to chargeBackup(...)
 *                                   returns ANOTHER CompletableFuture,
 *                                   and composing (not nesting) keeps the
 *                                   whole chain a single flat
 *                                   CompletableFuture<PaymentResult>
 *                                   rather than a
 *                                   CompletableFuture<CompletableFuture<PaymentResult>>
 *   .handle(...)                 — the terminal step, called regardless
 *                                   of whether everything above succeeded
 *                                   or every fallback also failed —
 *                                   guarantees exactly one outcome is
 *                                   always produced and logged, with no
 *                                   path that can silently vanish
 */
public class PaymentOrchestrator {

    private final PaymentGatewayClient primaryGateway;
    private final PaymentGatewayClient backupGateway;
    private final FraudCheckService fraudCheckService;

    public PaymentOrchestrator(PaymentGatewayClient primaryGateway,
                                PaymentGatewayClient backupGateway,
                                FraudCheckService fraudCheckService) {
        this.primaryGateway = primaryGateway;
        this.backupGateway = backupGateway;
        this.fraudCheckService = fraudCheckService;
    }

    public CompletableFuture<OrderOutcome> processPayment(long orderId, double amount) {
        // Fraud check starts immediately, concurrently with the charge —
        // no reason to serialize two calls that don't depend on each
        // other's output.
        CompletableFuture<FraudCheckResult> fraudCheckFuture = fraudCheckService.check(orderId, amount);

        CompletableFuture<PaymentResult> paymentFuture = primaryGateway.charge(orderId, amount)
                .orTimeout(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                .exceptionallyCompose(primaryFailure ->
                        // The primary gateway either threw a real
                        // GatewayException OR orTimeout gave up and threw
                        // TimeoutException wrapped in CompletionException
                        // — both land here identically, and both get the
                        // same fallback treatment: try the backup.
                        backupGateway.charge(orderId, amount));

        return paymentFuture.thenCombine(fraudCheckFuture, (payment, fraudCheck) -> {
                    String status = fraudCheck.approved() ? "COMPLETED" : "HELD_FOR_REVIEW";
                    return new OrderOutcome(orderId, payment, fraudCheck, status);
                })
                .handle((outcome, error) -> {
                    if (error != null) {
                        // Both the primary AND the backup gateway failed
                        // (or something else went wrong) — this is the
                        // ONE place a total failure is guaranteed to be
                        // observed and logged, no matter which upstream
                        // step actually failed.
                        Throwable cause = unwrap(error);
                        System.out.println("  order " + orderId + " FAILED entirely: " + cause.getMessage());
                        return new OrderOutcome(orderId, null, null, "FAILED");
                    }
                    return outcome;
                });
    }

    /** CompletionException wraps the real cause when it crosses an async boundary — see phase notes §4. */
    private static Throwable unwrap(Throwable t) {
        return (t instanceof CompletionException && t.getCause() != null) ? t.getCause() : t;
    }
}
