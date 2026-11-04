package com.orderengine.phase9;

import java.util.concurrent.StructuredTaskScope;

/**
 * PREVIEW API in Java 21 (JEP 453) — requires --enable-preview to
 * compile and run; see the exact commands in the phase notes. The API
 * shape has continued evolving in later JDK releases as it moves toward
 * finalization, so always check the docs for the JDK actually in use in
 * production rather than assuming this exact shape is permanent.
 *
 * StructuredTaskScope gives concurrent subtasks a single, clear PARENT
 * lifetime — every subtask forked inside the try-with-resources block is
 * guaranteed to either complete or be cancelled before the block exits.
 * This is a genuinely different, stronger guarantee than Phase 7's raw
 * CompletableFuture composition provides: a CompletableFuture chain has
 * NO structural relationship enforced between a "parent" operation and
 * the async tasks it kicks off — if the code that created a
 * CompletableFuture returns (or throws) without ever calling get()/join()
 * on it, that future's work simply keeps running in the background,
 * completely detached, with nothing tracking whether it ever finishes or
 * what happens to its result. That's precisely Phase 7's "unobserved
 * exception vanishes" pitfall from one angle — StructuredTaskScope closes
 * that gap structurally, not just by convention or careful discipline.
 *
 * ShutdownOnFailure: if ANY forked subtask fails, the scope immediately
 * cancels every OTHER still-running subtask rather than letting them run
 * to completion wastefully — fail-fast, with automatic cleanup. Used
 * here for the payment+fraud-check pair (mirroring Phase 7's
 * PaymentOrchestrator): if either call fails, there's no reason to keep
 * waiting on the other.
 *
 * ShutdownOnSuccess: the opposite — completes as soon as ANY forked
 * subtask succeeds, cancelling the rest. This is a genuinely stronger
 * version of Phase 7's CompletableFuture.anyOf(): anyOf does NOT cancel
 * the losing futures — they keep running to completion in the
 * background regardless, wasting whatever resources they were using.
 * ShutdownOnSuccess actually cancels them.
 */
public class StructuredConcurrencyDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== ShutdownOnFailure: fail-fast, auto-cancel the other subtask ===");
        demoShutdownOnFailure();

        System.out.println("\n=== ShutdownOnSuccess: first success wins, others actually cancelled ===");
        demoShutdownOnSuccess();
    }

    private static void demoShutdownOnFailure() throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            StructuredTaskScope.Subtask<String> paymentResult = scope.fork(() -> chargeGateway(1L));
            StructuredTaskScope.Subtask<String> fraudResult = scope.fork(() -> fraudCheck(1L));

            scope.join();           // waits for both subtasks (or early exit on first failure)
            scope.throwIfFailed();  // rethrows the first failure, if any, as this thread's own exception

            // Reaching here means BOTH subtasks succeeded — safe to call .get() on each.
            System.out.println("  payment: " + paymentResult.get() + ", fraud check: " + fraudResult.get());
        } catch (Exception e) {
            System.out.println("  scope failed as a whole: " + e.getMessage()
                    + "  (the sibling subtask was automatically cancelled, not left running)");
        }
    }

    private static void demoShutdownOnSuccess() throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
            scope.fork(() -> replicaCall("replica-A", 200));
            scope.fork(() -> replicaCall("replica-B", 50));  // fastest — should win
            scope.fork(() -> replicaCall("replica-C", 300));

            scope.join();
            String fastest = scope.result(); // throws if none succeeded
            System.out.println("  fastest replica response: " + fastest
                    + "  (the other two calls were cancelled once this one won, not left running to completion)");
        }
    }

    private static String chargeGateway(long orderId) throws InterruptedException {
        Thread.sleep(100);
        return "txn-" + orderId;
    }

    private static String fraudCheck(long orderId) throws InterruptedException {
        Thread.sleep(60);
        return "approved";
    }

    private static String replicaCall(String name, long delayMillis) throws InterruptedException {
        Thread.sleep(delayMillis);
        return name;
    }
}
