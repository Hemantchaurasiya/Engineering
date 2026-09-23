package com.orderengine.phase13;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The capstone demo for this phase: starts a real, live HTTP dashboard,
 * then drives load against a simulated operation while the dashboard is
 * reachable — proving metrics are visible DURING the run, not just
 * summarized afterward — and finally captures a thread dump snapshot
 * partway through, showing the kind of state breakdown a real triage
 * session would examine.
 *
 * While this is running, open a browser (or curl) to
 * http://localhost:8089/metrics and refresh during the stress test to
 * watch the counters and latency percentiles update live.
 */
public class EngineObservabilityDemo {

    public static void main(String[] args) throws Exception {
        MetricsRegistry metrics = new MetricsRegistry();
        HealthDashboardServer dashboard = new HealthDashboardServer(metrics, 8089);
        dashboard.start();

        System.out.println("Dashboard is live — try: curl http://localhost:8089/metrics");
        System.out.println("(waiting 2s before load starts, so you have time to open it)\n");
        Thread.sleep(2000);

        StressTestHarness harness = new StressTestHarness(metrics);

        // A separate thread captures a mid-run thread dump snapshot,
        // simulating an on-call engineer pulling a dump WHILE the
        // system is under load, exactly the real scenario this tooling
        // is for.
        Thread dumpThread = new Thread(() -> {
            sleep(500);
            System.out.println("\n--- mid-run thread dump snapshot ---");
            new ThreadDumpAnalyzer().printSummary();
            System.out.println("--- end snapshot ---\n");
        });
        dumpThread.start();

        harness.run("simulated-payment-call", 2000, 100, () -> {
            // simulated variable-latency downstream call
            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 150));
            if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                throw new RuntimeException("simulated failure");
            }
            return null;
        });

        dumpThread.join();

        System.out.println("\nFinal metrics (also still live at http://localhost:8089/metrics "
                + "for a few more seconds):");
        metrics.allCounters().forEach((k, v) -> System.out.println("  " + k + " = " + v));

        Thread.sleep(3000); // leave the dashboard up briefly so a manual curl/browser check can still see it
        dashboard.stop();
        System.out.println("\nDashboard stopped. Demo complete.");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
