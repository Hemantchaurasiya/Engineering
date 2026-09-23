package com.orderengine.phase12;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ChaosTestHarness {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Full lifecycle: healthy -> failing -> recovery ===");
        runLifecycleTest();

        System.out.println("\n=== Bulkhead isolation: a hung dependency cannot starve a healthy one ===");
        runBulkheadIsolationTest();
    }

    private static void runLifecycleTest() throws InterruptedException {
        FlakyPaymentGateway realGateway = new FlakyPaymentGateway();
        ResilientPaymentGateway resilientGateway = new ResilientPaymentGateway(realGateway);
        AtomicLong orderId = new AtomicLong(1);

        System.out.println("\n-- Phase A: healthy, 3 seconds of normal traffic --");
        realGateway.setHealth(FlakyPaymentGateway.Health.HEALTHY);
        driveTraffic(resilientGateway, orderId, 3000, 20);
        resilientGateway.printStats();

        System.out.println("\n-- Phase B: gateway starts FAILING — circuit breaker should trip OPEN --");
        realGateway.setHealth(FlakyPaymentGateway.Health.FAILING);
        driveTraffic(resilientGateway, orderId, 2000, 20);
        resilientGateway.printStats();
        System.out.println("  circuit state after failing phase: " + resilientGateway.circuitState()
                + " (expect OPEN — enough consecutive failures should have tripped it)");

        System.out.println("\n-- Phase C: still failing, but circuit is OPEN — calls should be rejected FAST, "
                + "not wait out the gateway's own latency --");
        long start = System.currentTimeMillis();
        driveTraffic(resilientGateway, orderId, 1000, 20);
        long elapsed = System.currentTimeMillis() - start;
        resilientGateway.printStats();
        System.out.println("  phase C took " + elapsed + "ms for its traffic window — rejections via an OPEN "
                + "circuit are near-instant, unlike phase B's calls which each had to wait out the gateway's "
                + "own (admittedly short, in this simulation) failure latency");

        System.out.println("\n-- Phase D: gateway RECOVERS to healthy — circuit should probe via HALF_OPEN and close --");
        realGateway.setHealth(FlakyPaymentGateway.Health.HEALTHY);
        // Wait out the cooldown so the breaker is willing to probe again.
        Thread.sleep(1100);
        driveTraffic(resilientGateway, orderId, 2000, 20);
        resilientGateway.printStats();
        System.out.println("  circuit state after recovery phase: " + resilientGateway.circuitState()
                + " (expect CLOSED — the probe succeeded and normal traffic resumed)");

        resilientGateway.shutdown();
    }

    private static void driveTraffic(ResilientPaymentGateway gateway, AtomicLong orderId,
                                      long durationMillis, int callsPerSecond) throws InterruptedException {
        long end = System.currentTimeMillis() + durationMillis;
        long delayBetweenCalls = 1000 / callsPerSecond;
        while (System.currentTimeMillis() < end) {
            gateway.charge(orderId.getAndIncrement());
            Thread.sleep(delayBetweenCalls);
        }
    }

    private static void runBulkheadIsolationTest() throws InterruptedException {
        Bulkhead paymentBulkhead = new Bulkhead("payment", 5);
        Bulkhead fraudCheckBulkhead = new Bulkhead("fraud-check", 5);

        AtomicLong fraudCheckSuccesses = new AtomicLong(0);
        AtomicLong fraudCheckFailures = new AtomicLong(0);

        // Saturate the PAYMENT bulkhead with calls that hang far longer
        // than its timeout — simulating a completely hung dependency.
        Thread[] hungPaymentCalls = new Thread[20];
        for (int i = 0; i < hungPaymentCalls.length; i++) {
            hungPaymentCalls[i] = new Thread(() -> {
                try {
                    paymentBulkhead.call(() -> {
                        Thread.sleep(10_000); // simulates a hung call, far longer than any reasonable timeout
                        return "unreachable";
                    }, 300, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    // expected — the bulkhead call times out
                }
            });
            hungPaymentCalls[i].setDaemon(true);
            hungPaymentCalls[i].start();
        }

        Thread.sleep(200); // let the payment bulkhead actually become saturated

        // Meanwhile, fraud-check calls on their OWN separate bulkhead
        // should be completely unaffected by the payment bulkhead being
        // 100% hung.
        Thread[] fraudCheckCalls = new Thread[20];
        for (int i = 0; i < fraudCheckCalls.length; i++) {
            fraudCheckCalls[i] = new Thread(() -> {
                try {
                    String result = fraudCheckBulkhead.call(() -> {
                        Thread.sleep(20); // fraud check is healthy and fast
                        return "approved";
                    }, 500, TimeUnit.MILLISECONDS);
                    if ("approved".equals(result)) fraudCheckSuccesses.incrementAndGet();
                } catch (Exception e) {
                    fraudCheckFailures.incrementAndGet();
                }
            });
            fraudCheckCalls[i].start();
        }
        for (Thread t : fraudCheckCalls) t.join(2000);

        System.out.println("  payment bulkhead: fully saturated with 20 hung calls (each will time out at 300ms)");
        System.out.println("  fraud-check bulkhead (SEPARATE pool): successes=" + fraudCheckSuccesses.get()
                + " failures=" + fraudCheckFailures.get() + " out of " + fraudCheckCalls.length + " calls");
        System.out.println("  RESULT: " + (fraudCheckSuccesses.get() == fraudCheckCalls.length
                ? "OK — fraud-check was completely unaffected by the payment dependency being 100% hung, "
                        + "because they never shared any thread pool capacity"
                : "unexpected — investigate whether the pools are actually isolated"));

        paymentBulkhead.shutdown();
        fraudCheckBulkhead.shutdown();
    }
}
